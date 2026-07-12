package com.example.aigateway.service;

import com.example.aigateway.adapter.ModelAdapter;
import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelResponse;
import com.example.aigateway.dto.ModelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.*;

class ModelRoutingServiceTest {

    @Mock
    private ModelAdapter openAiAdapter;

    @Mock
    private ModelAdapter deepSeekAdapter;

    private ModelRoutingService routingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(openAiAdapter.supports(ModelType.OPENAI)).thenReturn(true);
        when(deepSeekAdapter.supports(ModelType.DEEPSEEK)).thenReturn(true);
        
        routingService = new ModelRoutingService(List.of(openAiAdapter, deepSeekAdapter));
    }

    @Test
    void testRouteChatWithHealthyAdapter() {
        when(openAiAdapter.healthCheck()).thenReturn(Mono.just(true));
        
        ModelResponse expectedResponse = ModelResponse.builder()
            .content("test response")
            .model("gpt-3.5-turbo")
            .build();
        
        when(openAiAdapter.chat(any())).thenReturn(Mono.just(expectedResponse));

        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .model("gpt-3.5-turbo")
            .build();

        StepVerifier.create(routingService.routeChat(request))
            .expectNextMatches(response -> "test response".equals(response.getContent()))
            .verifyComplete();

        verify(openAiAdapter).chat(request);
    }

    @Test
    void testRouteChatWithFallback() {
        when(openAiAdapter.healthCheck()).thenReturn(Mono.just(false));
        when(deepSeekAdapter.healthCheck()).thenReturn(Mono.just(true));

        ModelResponse expectedResponse = ModelResponse.builder()
            .content("fallback response")
            .model("deepseek-chat")
            .build();

        when(deepSeekAdapter.chat(any())).thenReturn(Mono.just(expectedResponse));

        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .model("gpt-3.5-turbo")
            .build();

        StepVerifier.create(routingService.routeChat(request))
            .expectNextMatches(response -> "fallback response".equals(response.getContent()))
            .verifyComplete();

        verify(deepSeekAdapter).chat(request);
    }

    @Test
    void testRouteChatNoHealthyAdapter() {
        when(openAiAdapter.healthCheck()).thenReturn(Mono.just(false));
        when(deepSeekAdapter.healthCheck()).thenReturn(Mono.just(false));

        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .build();

        StepVerifier.create(routingService.routeChat(request))
            .expectError(RuntimeException.class)
            .verify();
    }

    @Test
    void testRouteStreamChat() {
        when(openAiAdapter.healthCheck()).thenReturn(Mono.just(true));
        when(openAiAdapter.streamChat(any())).thenReturn(Flux.just("Hello", " ", "World"));

        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .model("gpt-3.5-turbo")
            .build();

        StepVerifier.create(routingService.routeStreamChat(request))
            .expectNext("Hello")
            .expectNext(" ")
            .expectNext("World")
            .verifyComplete();
    }

    @Test
    void testGetHealthStatus() {
        when(openAiAdapter.healthCheck()).thenReturn(Mono.just(true));
        when(deepSeekAdapter.healthCheck()).thenReturn(Mono.just(false));

        StepVerifier.create(routingService.getHealthStatus())
            .expectNextMatches(map -> 
                map.containsKey(ModelType.OPENAI) && 
                map.containsKey(ModelType.DEEPSEEK) &&
                map.get(ModelType.OPENAI) &&
                !map.get(ModelType.DEEPSEEK)
            )
            .verifyComplete();
    }
}