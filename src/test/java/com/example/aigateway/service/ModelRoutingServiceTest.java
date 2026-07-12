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
    private ModelAdapter claudeAdapter;

    @Mock
    private ModelAdapter deepSeekAdapter;

    @Mock
    private ModelAdapter tongYiAdapter;

    private ModelRoutingService routingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(openAiAdapter.supports(ModelType.OPENAI)).thenReturn(true);
        when(claudeAdapter.supports(ModelType.CLAUDE)).thenReturn(true);
        when(deepSeekAdapter.supports(ModelType.DEEPSEEK)).thenReturn(true);
        when(tongYiAdapter.supports(ModelType.TONGYI)).thenReturn(true);
        
        when(openAiAdapter.healthCheck()).thenReturn(Mono.just(true));
        when(claudeAdapter.healthCheck()).thenReturn(Mono.just(true));
        when(deepSeekAdapter.healthCheck()).thenReturn(Mono.just(true));
        when(tongYiAdapter.healthCheck()).thenReturn(Mono.just(true));
        
        routingService = new ModelRoutingService(List.of(openAiAdapter, claudeAdapter, deepSeekAdapter, tongYiAdapter));
    }

    @Test
    void testRouteChatWithHealthyAdapter() {
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

        verify(openAiAdapter).chat(any());
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

        verify(deepSeekAdapter).chat(any());
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
        when(claudeAdapter.healthCheck()).thenReturn(Mono.just(false));
        when(deepSeekAdapter.healthCheck()).thenReturn(Mono.just(true));
        when(tongYiAdapter.healthCheck()).thenReturn(Mono.just(false));

        StepVerifier.create(routingService.getHealthStatus())
            .expectNextMatches(map -> 
                map.containsKey(ModelType.OPENAI) && 
                map.containsKey(ModelType.CLAUDE) &&
                map.containsKey(ModelType.DEEPSEEK) &&
                map.containsKey(ModelType.TONGYI) &&
                map.get(ModelType.OPENAI) &&
                !map.get(ModelType.CLAUDE) &&
                map.get(ModelType.DEEPSEEK) &&
                !map.get(ModelType.TONGYI)
            )
            .verifyComplete();
    }

    @Test
    void testRouteChatToClaude() {
        ModelResponse expectedResponse = ModelResponse.builder()
            .content("claude response")
            .model("claude-3-sonnet")
            .build();
        
        when(claudeAdapter.chat(any())).thenReturn(Mono.just(expectedResponse));

        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.CLAUDE)
            .model("claude-3-sonnet")
            .build();

        StepVerifier.create(routingService.routeChat(request))
            .expectNextMatches(response -> "claude response".equals(response.getContent()))
            .verifyComplete();

        verify(claudeAdapter).chat(any());
        verify(openAiAdapter, never()).chat(any());
    }

    @Test
    void testRouteChatToDeepSeek() {
        ModelResponse expectedResponse = ModelResponse.builder()
            .content("deepseek response")
            .model("deepseek-chat")
            .build();
        
        when(deepSeekAdapter.chat(any())).thenReturn(Mono.just(expectedResponse));

        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.DEEPSEEK)
            .model("deepseek-chat")
            .build();

        StepVerifier.create(routingService.routeChat(request))
            .expectNextMatches(response -> "deepseek response".equals(response.getContent()))
            .verifyComplete();

        verify(deepSeekAdapter).chat(any());
    }

    @Test
    void testRouteChatToTongYi() {
        ModelResponse expectedResponse = ModelResponse.builder()
            .content("tongyi response")
            .model("qwen-7b-chat")
            .build();
        
        when(tongYiAdapter.chat(any())).thenReturn(Mono.just(expectedResponse));

        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.TONGYI)
            .model("qwen-7b-chat")
            .build();

        StepVerifier.create(routingService.routeChat(request))
            .expectNextMatches(response -> "tongyi response".equals(response.getContent()))
            .verifyComplete();

        verify(tongYiAdapter).chat(any());
    }

    @Test
    void testFallbackChain() {
        when(openAiAdapter.healthCheck()).thenReturn(Mono.just(false));
        when(deepSeekAdapter.healthCheck()).thenReturn(Mono.just(false));
        when(tongYiAdapter.healthCheck()).thenReturn(Mono.just(true));

        ModelResponse expectedResponse = ModelResponse.builder()
            .content("tongyi fallback response")
            .model("qwen-7b-chat")
            .build();

        when(tongYiAdapter.chat(any())).thenReturn(Mono.just(expectedResponse));

        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .build();

        StepVerifier.create(routingService.routeChat(request))
            .expectNextMatches(response -> "tongyi fallback response".equals(response.getContent()))
            .verifyComplete();
    }

    @Test
    void testCustomFallback() {
        routingService.setFallback(ModelType.OPENAI, ModelType.CLAUDE);
        
        when(openAiAdapter.healthCheck()).thenReturn(Mono.just(false));
        when(claudeAdapter.healthCheck()).thenReturn(Mono.just(true));

        ModelResponse expectedResponse = ModelResponse.builder()
            .content("claude custom fallback")
            .model("claude-3-sonnet")
            .build();

        when(claudeAdapter.chat(any())).thenReturn(Mono.just(expectedResponse));

        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .build();

        StepVerifier.create(routingService.routeChat(request))
            .expectNextMatches(response -> "claude custom fallback".equals(response.getContent()))
            .verifyComplete();
    }

    @Test
    void testUnsupportedModelType() {
        ModelRequest request = ModelRequest.builder()
            .modelType(null)
            .build();

        StepVerifier.create(routingService.routeChat(request))
            .expectError(IllegalArgumentException.class)
            .verify();
    }

    @Test
    void testRouteStreamChatWithFallback() {
        when(openAiAdapter.healthCheck()).thenReturn(Mono.just(false));
        when(deepSeekAdapter.healthCheck()).thenReturn(Mono.just(true));
        
        when(deepSeekAdapter.streamChat(any())).thenReturn(Flux.just("fallback", " ", "stream"));

        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .build();

        StepVerifier.create(routingService.routeStreamChat(request))
            .expectNext("fallback")
            .expectNext(" ")
            .expectNext("stream")
            .verifyComplete();
    }
}