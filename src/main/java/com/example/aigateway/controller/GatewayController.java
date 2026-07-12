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
    public Mono<ResponseEntity<ModelResponse>> chat(@RequestBody ModelRequest request) {
        log.info("Received chat request for model type: {}", request.getModelType());
        return routingService.routeChat(request)
            .map(ResponseEntity::ok);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimiter(name = "ai-gateway", fallbackMethod = "streamChatFallback")
    @CircuitBreaker(name = "ai-gateway", fallbackMethod = "streamChatFallback")
    public Flux<String> streamChat(@RequestBody ModelRequest request) {
        log.info("Received stream chat request for model type: {}", request.getModelType());
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

    @SuppressWarnings("unused")
    private Mono<ResponseEntity<ModelResponse>> chatFallback(ModelRequest request, Throwable ex) {
        log.warn("Chat fallback triggered: {}", ex.getMessage());
        ModelResponse fallbackResponse = ModelResponse.builder()
            .content("服务暂时不可用，请稍后重试")
            .model("fallback")
            .build();
        return Mono.just(ResponseEntity.status(503).body(fallbackResponse));
    }

    @SuppressWarnings("unused")
    private Flux<String> streamChatFallback(ModelRequest request, Throwable ex) {
        log.warn("Stream chat fallback triggered: {}", ex.getMessage());
        return Flux.just("服务暂时不可用，请稍后重试");
    }
}