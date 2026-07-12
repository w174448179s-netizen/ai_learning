package com.example.aigateway.integration;

import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelResponse;
import com.example.aigateway.dto.ModelType;
import com.example.aigateway.service.ModelRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ModelRoutingService routingService;

    private String validApiKey = "Bearer sk-demo-key";
    private String invalidApiKey = "Bearer sk-invalid-key";

    @BeforeEach
    void setUp() {
        routingService.clearCanary();
    }

    @Test
    void testChatEndpointSuccess() {
        webTestClient.post()
            .uri("/api/v1/chat")
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "OPENAI",
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", "Hello"))
            ))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().exists("X-Trace-Id");
    }

    @Test
    void testStreamChatEndpointSuccess() {
        webTestClient.post()
            .uri("/api/v1/chat/stream")
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "OPENAI",
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", "Hello"))
            ))
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.TEXT_EVENT_STREAM)
            .expectHeader().exists("X-Trace-Id");
    }

    @Test
    void testApiKeyMissingReturns401() {
        webTestClient.post()
            .uri("/api/v1/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "OPENAI",
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", "Hello"))
            ))
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.error").isEqualTo("Unauthorized")
            .jsonPath("$.message").isEqualTo("API key is required");
    }

    @Test
    void testInvalidApiKeyReturns403() {
        webTestClient.post()
            .uri("/api/v1/chat")
            .header(HttpHeaders.AUTHORIZATION, invalidApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "OPENAI",
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", "Hello"))
            ))
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.error").isEqualTo("Forbidden")
            .jsonPath("$.message").isEqualTo("Invalid API key");
    }

    @Test
    void testTraceIdPresentInResponse() {
        String traceId = webTestClient.post()
            .uri("/api/v1/chat")
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "OPENAI",
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", "Hello"))
            ))
            .exchange()
            .expectStatus().isOk()
            .returnResult(Void.class)
            .getResponseHeaders()
            .getFirst("X-Trace-Id");

        assertNotNull(traceId);
        assertFalse(traceId.isEmpty());
        assertEquals(32, traceId.length());
    }

    @Test
    void testCanaryWhitelistRouting() {
        routingService.setCanary(ModelType.OPENAI, ModelType.DEEPSEEK, 0);
        routingService.addCanaryWhitelist("test-client-123");

        webTestClient.post()
            .uri("/api/v1/chat")
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .header("X-Client-Id", "test-client-123")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "OPENAI",
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", "Hello"))
            ))
            .exchange()
            .expectStatus().isOk();

        routingService.removeCanaryWhitelist("test-client-123");
    }

    @Test
    void testCanaryWeightedRouting() {
        routingService.setCanary(ModelType.OPENAI, ModelType.DEEPSEEK, 100);

        webTestClient.post()
            .uri("/api/v1/chat")
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .header("X-Client-Id", "random-client")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "OPENAI",
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", "Hello"))
            ))
            .exchange()
            .expectStatus().isOk();

        routingService.clearCanary();
    }

    @Test
    void testHealthEndpointPublic() {
        webTestClient.get()
            .uri("/api/v1/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void testModelsEndpointPublic() {
        webTestClient.get()
            .uri("/api/v1/models")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(4);
    }

    @Test
    void testStreamChatEndpointWithApiKey() {
        webTestClient.post()
            .uri("/api/v1/chat/stream")
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "OPENAI",
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", "Hello"))
            ))
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void testStreamChatEndpointWithoutApiKeyReturns401() {
        webTestClient.post()
            .uri("/api/v1/chat/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "OPENAI",
                "model", "gpt-3.5-turbo",
                "messages", List.of(Map.of("role", "user", "content", "Hello"))
            ))
            .exchange()
            .expectStatus().isUnauthorized();
    }

    @Test
    void testChatEndpointInvalidModelType() {
        webTestClient.post()
            .uri("/api/v1/chat")
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "UNKNOWN",
                "model", "unknown-model",
                "messages", List.of(Map.of("role", "user", "content", "Hello"))
            ))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void testChatEndpointMissingMessages() {
        webTestClient.post()
            .uri("/api/v1/chat")
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "OPENAI",
                "model", "gpt-3.5-turbo"
            ))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void testChatEndpointEmptyMessages() {
        webTestClient.post()
            .uri("/api/v1/chat")
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "modelType", "OPENAI",
                "model", "gpt-3.5-turbo",
                "messages", List.of()
            ))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void testCanaryConfigureEndpoint() {
        webTestClient.post()
            .uri("/api/v1/canary/configure?primary=OPENAI&canary=DEEPSEEK&weight=10")
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .isEqualTo("Canary routing configured: OPENAI -> DEEPSEEK with weight 10");
    }

    @Test
    void testCanaryWhitelistEndpoint() {
        String clientId = "client-" + UUID.randomUUID();
        
        webTestClient.post()
            .uri("/api/v1/canary/whitelist?clientId=" + clientId)
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .exchange()
            .expectStatus().isOk();

        webTestClient.delete()
            .uri("/api/v1/canary/whitelist?clientId=" + clientId)
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .exchange()
            .expectStatus().isOk();
    }

    @Test
    void testCanaryClearEndpoint() {
        routingService.setCanary(ModelType.OPENAI, ModelType.DEEPSEEK, 50);

        webTestClient.delete()
            .uri("/api/v1/canary")
            .header(HttpHeaders.AUTHORIZATION, validApiKey)
            .exchange()
            .expectStatus().isOk();
    }
}