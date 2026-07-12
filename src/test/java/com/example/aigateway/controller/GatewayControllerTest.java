package com.example.aigateway.controller;

import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelResponse;
import com.example.aigateway.dto.ModelType;
import com.example.aigateway.service.ModelRoutingService;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

class GatewayControllerTest {

    @Mock
    private ModelRoutingService routingService;

    @Mock
    private CircuitBreakerOperator<Object> circuitBreakerOperator;

    @Mock
    private RateLimiterOperator<Object> rateLimiterOperator;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(circuitBreakerOperator.apply(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rateLimiterOperator.apply(any())).thenAnswer(invocation -> invocation.getArgument(0));
        
        GatewayController controller = new GatewayController(routingService, circuitBreakerOperator, rateLimiterOperator);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void testChatEndpoint() {
        ModelResponse expectedResponse = ModelResponse.builder()
            .content("Hello, world!")
            .model("gpt-3.5-turbo")
            .build();

        when(routingService.routeChat(any())).thenReturn(Mono.just(expectedResponse));

        webTestClient.post()
            .uri("/api/v1/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "OPENAI",
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", "Hello"))
            ))
            .exchange()
            .expectStatus().isOk()
            .expectBody(ModelResponse.class)
            .consumeWith(result -> {
                ModelResponse response = result.getResponseBody();
                assert response != null;
                assert "Hello, world!".equals(response.getContent());
            });
    }

    @Test
    void testStreamChatEndpoint() {
        when(routingService.routeStreamChat(any()))
            .thenReturn(Flux.just("Hello", " ", "World"));

        webTestClient.post()
            .uri("/api/v1/chat/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "OPENAI",
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", "Hello")),
                "stream", true
            ))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    void testHealthEndpoint() {
        when(routingService.getHealthStatus())
            .thenReturn(Mono.just(Map.of(ModelType.OPENAI, true, ModelType.DEEPSEEK, false)));

        webTestClient.get()
            .uri("/api/v1/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.modelProviders.OPENAI").isEqualTo(true);
    }

    @Test
    void testGetSupportedModels() {
        webTestClient.get()
            .uri("/api/v1/models")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(4);
    }
}