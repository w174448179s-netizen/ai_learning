package com.example.aigateway.service;

import com.example.aigateway.adapter.ModelAdapter;
import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelResponse;
import com.example.aigateway.dto.ModelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRoutingService {

    private final List<ModelAdapter> modelAdapters;
    private final Map<ModelType, ModelType> fallbackMap = new ConcurrentHashMap<>();
    private final Map<ModelType, Boolean> healthCache = new ConcurrentHashMap<>();
    private final Map<ModelType, Instant> healthCacheTime = new ConcurrentHashMap<>();
    
    private final Map<ModelType, ModelType> canaryMap = new ConcurrentHashMap<>();
    private final Map<ModelType, Integer> canaryWeightMap = new ConcurrentHashMap<>();
    private final Set<String> canaryWhitelist = new ConcurrentSkipListSet<>();
    
    private static final long CACHE_TTL_SECONDS = 30;
    private static final Random RANDOM = new Random();

    @PostConstruct
    public void init() {
        refreshHealthCache();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::refreshHealthCache, CACHE_TTL_SECONDS, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("Health check scheduler started with {}s interval", CACHE_TTL_SECONDS);
    }

    private void refreshHealthCache() {
        log.debug("Refreshing health cache...");
        modelAdapters.forEach(adapter -> {
            ModelType type = findModelType(adapter);
            adapter.healthCheck().subscribe(
                healthy -> {
                    healthCache.put(type, healthy);
                    healthCacheTime.put(type, Instant.now());
                    if (!healthy) {
                        log.warn("Model {} is unhealthy", type);
                    }
                },
                error -> {
                    healthCache.put(type, false);
                    healthCacheTime.put(type, Instant.now());
                    log.warn("Health check failed for model {}: {}", type, error.getMessage());
                }
            );
        });
    }

    private boolean isHealthy(ModelType modelType) {
        Instant lastCheck = healthCacheTime.get(modelType);
        if (lastCheck != null && Instant.now().isBefore(lastCheck.plusSeconds(CACHE_TTL_SECONDS))) {
            return healthCache.getOrDefault(modelType, false);
        }
        return false;
    }

    public Mono<ModelResponse> routeChat(ModelRequest request) {
        return findHealthyAdapter(request.getModelType())
            .flatMap(adapter -> adapter.chat(request));
    }

    public Flux<String> routeStreamChat(ModelRequest request) {
        return findHealthyAdapter(request.getModelType())
            .flatMapMany(adapter -> adapter.streamChat(request));
    }

    public Mono<ModelResponse> routeChatWithCanary(ModelRequest request, String clientId) {
        ModelType targetType = applyCanaryRouting(request.getModelType(), clientId);
        request = ModelRequest.builder()
            .modelType(targetType)
            .model(request.getModel())
            .messages(request.getMessages())
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxTokens())
            .topP(request.getTopP())
            .stream(request.getStream())
            .extras(request.getExtras())
            .build();
        
        return routeChat(request);
    }

    public Flux<String> routeStreamChatWithCanary(ModelRequest request, String clientId) {
        ModelType targetType = applyCanaryRouting(request.getModelType(), clientId);
        request = ModelRequest.builder()
            .modelType(targetType)
            .model(request.getModel())
            .messages(request.getMessages())
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxTokens())
            .topP(request.getTopP())
            .stream(request.getStream())
            .extras(request.getExtras())
            .build();
        
        return routeStreamChat(request);
    }

    private ModelType applyCanaryRouting(ModelType originalType, String clientId) {
        ModelType canaryType = canaryMap.get(originalType);
        if (canaryType == null) {
            return originalType;
        }

        if (canaryWhitelist.contains(clientId)) {
            log.debug("Client {} is in canary whitelist, routing to {}", clientId, canaryType);
            return canaryType;
        }

        Integer weight = canaryWeightMap.getOrDefault(originalType, 0);
        if (weight > 0 && RANDOM.nextInt(100) < weight) {
            log.debug("Random canary routing triggered for client {}, routing to {}", clientId, canaryType);
            return canaryType;
        }

        return originalType;
    }

    public Mono<Map<ModelType, Boolean>> getHealthStatus() {
        return Flux.fromIterable(modelAdapters)
            .flatMap(adapter -> {
                ModelType type = findModelType(adapter);
                return adapter.healthCheck()
                    .map(healthy -> Map.entry(type, healthy));
            })
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private Mono<ModelAdapter> findHealthyAdapter(ModelType modelType) {
        ModelAdapter primaryAdapter = findAdapter(modelType);
        
        if (isHealthy(modelType)) {
            return Mono.just(primaryAdapter);
        }

        log.warn("Primary adapter for {} is unhealthy, attempting fallback", modelType);
        
        ModelType fallbackType = fallbackMap.getOrDefault(modelType, getDefaultFallback(modelType));
        
        if (isHealthy(fallbackType)) {
            ModelAdapter fallbackAdapter = findAdapter(fallbackType);
            log.info("Using fallback adapter: {} -> {}", modelType, fallbackType);
            return Mono.just(fallbackAdapter);
        }
        
        return Mono.error(new RuntimeException("No healthy adapter available for model type: " + modelType));
    }

    private ModelAdapter findAdapter(ModelType modelType) {
        return modelAdapters.stream()
            .filter(adapter -> adapter.supports(modelType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported model type: " + modelType));
    }

    private ModelType findModelType(ModelAdapter adapter) {
        for (ModelType type : ModelType.values()) {
            if (adapter.supports(type)) {
                return type;
            }
        }
        return ModelType.OPENAI;
    }

    private ModelType getDefaultFallback(ModelType modelType) {
        return switch (modelType) {
            case OPENAI -> ModelType.DEEPSEEK;
            case CLAUDE -> ModelType.OPENAI;
            case DEEPSEEK -> ModelType.TONGYI;
            case TONGYI -> ModelType.OPENAI;
        };
    }

    public void setFallback(ModelType primary, ModelType fallback) {
        fallbackMap.put(primary, fallback);
    }

    public void setCanary(ModelType primary, ModelType canary, int weight) {
        canaryMap.put(primary, canary);
        canaryWeightMap.put(primary, weight);
        log.info("Canary routing configured: {} -> {} with weight {}", primary, canary, weight);
    }

    public void addCanaryWhitelist(String clientId) {
        canaryWhitelist.add(clientId);
        log.info("Added client {} to canary whitelist", clientId);
    }

    public void removeCanaryWhitelist(String clientId) {
        canaryWhitelist.remove(clientId);
        log.info("Removed client {} from canary whitelist", clientId);
    }

    public void clearCanary() {
        canaryMap.clear();
        canaryWeightMap.clear();
        log.info("Canary routing cleared");
    }
}