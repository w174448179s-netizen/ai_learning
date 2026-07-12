package com.example.aigateway.adapter;

import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelResponse;
import com.example.aigateway.dto.ModelType;
import com.example.aigateway.param.ParamNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
public class ClaudeAdapter extends AbstractModelAdapter {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ClaudeAdapter(ParamNormalizer paramNormalizer,
                         @Value("${spring.ai.claude.api-key:}") String apiKey,
                         @Value("${spring.ai.claude.base-url:https://api.anthropic.com/v1}") String baseUrl) {
        super(paramNormalizer, ModelType.CLAUDE);
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("x-api-key", apiKey)
            .defaultHeader("anthropic-version", "2023-06-01")
            .defaultHeader("Content-Type", "application/json")
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected Mono<ModelResponse> doChat(ModelRequest request) {
        return webClient.post()
            .uri("/messages")
            .bodyValue(Map.of(
                "model", request.getModel(),
                "max_tokens", request.getMaxTokens(),
                "temperature", request.getTemperature(),
                "messages", request.getMessages().stream()
                    .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                    .toList()
            ))
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(this::parseResponse);
    }

    @Override
    protected Flux<String> doStreamChat(ModelRequest request) {
        return webClient.post()
            .uri("/messages")
            .bodyValue(Map.of(
                "model", request.getModel(),
                "max_tokens", request.getMaxTokens(),
                "temperature", request.getTemperature(),
                "stream", true,
                "messages", request.getMessages().stream()
                    .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                    .toList()
            ))
            .retrieve()
            .bodyToFlux(String.class)
            .filter(s -> s.startsWith("data:") && !s.contains("[DONE]"))
            .map(s -> s.substring(6))
            .flatMap(this::parseStreamChunk);
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return webClient.get()
            .uri("/models")
            .retrieve()
            .toBodilessEntity()
            .map(response -> true)
            .onErrorReturn(false);
    }

    private ModelResponse parseResponse(JsonNode response) {
        String model = response.get("model").asText();
        JsonNode content = response.get("content").get(0);
        String text = content.get("text").asText();

        return ModelResponse.builder()
            .content(text)
            .model(model)
            .rawResponse(Map.of("response", response))
            .build();
    }

    private Flux<String> parseStreamChunk(String jsonString) {
        try {
            JsonNode node = objectMapper.readTree(jsonString);
            JsonNode type = node.get("type");
            if (type != null && "content_block_delta".equals(type.asText())) {
                JsonNode delta = node.get("delta");
                if (delta != null && delta.has("text")) {
                    String text = delta.get("text").asText();
                    if (!text.isEmpty()) {
                        return Flux.just(text);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse Claude stream chunk: {}", e.getMessage());
        }
        return Flux.empty();
    }
}