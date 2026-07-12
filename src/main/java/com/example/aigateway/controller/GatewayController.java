package com.example.aigateway.controller;

import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelResponse;
import com.example.aigateway.dto.ModelType;
import com.example.aigateway.service.ModelRoutingService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GatewayController {

    private final ModelRoutingService routingService;

    @PostMapping("/chat")
    @RateLimiter(name = "ai-gateway", fallbackMethod = "chatFallback")
    @CircuitBreaker(name = "ai-gateway", fallbackMethod = "chatFallback")
    public Mono<ResponseEntity<ModelResponse>> chat(@RequestBody ModelRequest request,
                                                    @RequestHeader(value = "X-Client-Id", required = false) String clientId) {
        log.info("Received chat request for model type: {}, clientId: {}", request.getModelType(), clientId);
        
        if (clientId != null) {
            return routingService.routeChatWithCanary(request, clientId)
                .map(ResponseEntity::ok);
        }
        
        return routingService.routeChat(request)
            .map(ResponseEntity::ok);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimiter(name = "ai-gateway", fallbackMethod = "streamChatFallback")
    @CircuitBreaker(name = "ai-gateway", fallbackMethod = "streamChatFallback")
    public Flux<String> streamChat(@RequestBody ModelRequest request,
                                   @RequestHeader(value = "X-Client-Id", required = false) String clientId) {
        log.info("Received stream chat request for model type: {}, clientId: {}", request.getModelType(), clientId);
        
        if (clientId != null) {
            return routingService.routeStreamChatWithCanary(request, clientId);
        }
        
        return routingService.routeStreamChat(request);
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        return routingService.getHealthStatus()
            .map(healthStatus -> ResponseEntity.ok(Map.of(
                "status", "UP",
                "modelProviders", healthStatus
            )));
    }

    @GetMapping("/models")
    public ResponseEntity<List<ModelType>> getSupportedModels() {
        return ResponseEntity.ok(List.of(ModelType.values()));
    }

    @PostMapping("/canary/configure")
    public ResponseEntity<String> configureCanary(@RequestParam ModelType primary,
                                                  @RequestParam ModelType canary,
                                                  @RequestParam(defaultValue = "10") int weight) {
        routingService.setCanary(primary, canary, weight);
        return ResponseEntity.ok("Canary routing configured: " + primary + " -> " + canary + " with weight " + weight);
    }

    @PostMapping("/canary/whitelist")
    public ResponseEntity<String> addToWhitelist(@RequestParam String clientId) {
        routingService.addCanaryWhitelist(clientId);
        return ResponseEntity.ok("Client " + clientId + " added to canary whitelist");
    }

    @DeleteMapping("/canary/whitelist")
    public ResponseEntity<String> removeFromWhitelist(@RequestParam String clientId) {
        routingService.removeCanaryWhitelist(clientId);
        return ResponseEntity.ok("Client " + clientId + " removed from canary whitelist");
    }

    @DeleteMapping("/canary")
    public ResponseEntity<String> clearCanary() {
        routingService.clearCanary();
        return ResponseEntity.ok("Canary routing cleared");
    }

    @SuppressWarnings("unused")
    private Mono<ResponseEntity<ModelResponse>> chatFallback(ModelRequest request, Throwable ex) {
        log.warn("Chat fallback triggered: {}", ex.getMessage());
        
        if (ex instanceof io.github.resilience4j.ratelimiter.RequestNotPermitted) {
            ModelResponse fallbackResponse = ModelResponse.builder()
                .content("请求过于频繁，请稍后重试")
                .model("rate-limited")
                .build();
            return Mono.just(ResponseEntity.status(429).body(fallbackResponse));
        }
        
        ModelResponse fallbackResponse = ModelResponse.builder()
            .content("服务暂时不可用，请稍后重试")
            .model("fallback")
            .build();
        return Mono.just(ResponseEntity.status(503).body(fallbackResponse));
    }

    @SuppressWarnings("unused")
    private Flux<String> streamChatFallback(ModelRequest request, Throwable ex) {
        log.warn("Stream chat fallback triggered: {}", ex.getMessage());
        
        if (ex instanceof io.github.resilience4j.ratelimiter.RequestNotPermitted) {
            return Flux.just("请求过于频繁，请稍后重试");
        }
        
        return Flux.just("服务暂时不可用，请稍后重试");
    }
}