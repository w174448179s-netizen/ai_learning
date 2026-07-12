package com.example.aigateway.adapter;

import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelResponse;
import com.example.aigateway.dto.ModelType;
import com.example.aigateway.dto.Message;
import com.example.aigateway.param.ParamNormalizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OpenAiAdapter extends AbstractModelAdapter {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiAdapter(ParamNormalizer paramNormalizer,
                         @Value("${spring.ai.openai.api-key}") String apiKey,
                         @Value("${spring.ai.openai.base-url}") String baseUrl) {
        super(paramNormalizer, ModelType.OPENAI);
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected Mono<ModelResponse> doChat(ModelRequest request) {
        return webClient.post()
            .uri("/chat/completions")
            .bodyValue(Map.of(
                "model", request.getModel(),
                "messages", convertMessages(request.getMessages()),
                "temperature", request.getTemperature(),
                "max_tokens", request.getMaxTokens(),
                "top_p", request.getTopP()
            ))
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(this::parseResponse);
    }

    @Override
    protected Flux<String> doStreamChat(ModelRequest request) {
        return webClient.post()
            .uri("/chat/completions")
            .bodyValue(Map.of(
                "model", request.getModel(),
                "messages", convertMessages(request.getMessages()),
                "temperature", request.getTemperature(),
                "max_tokens", request.getMaxTokens(),
                "top_p", request.getTopP(),
                "stream", true
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
        JsonNode choices = response.get("choices").get(0);
        String content = choices.get("message").get("content").asText();
        
        JsonNode usage = response.get("usage");
        
        return ModelResponse.builder()
            .content(content)
            .model(model)
            .promptTokens(usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0)
            .completionTokens(usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0)
            .totalTokens(usage.has("total_tokens") ? usage.get("total_tokens").asInt() : 0)
            .rawResponse(Map.of("response", response))
            .build();
    }

    private Flux<String> parseStreamChunk(String jsonString) {
        try {
            JsonNode node = objectMapper.readTree(jsonString);
            JsonNode choices = node.get("choices");
            if (choices != null && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null && delta.has("content")) {
                    String content = delta.get("content").asText();
                    if (!content.isEmpty()) {
                        return Flux.just(content);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse stream chunk: {}", e.getMessage());
        }
        return Flux.empty();
    }

    private List<Map<String, String>> convertMessages(List<Message> messages) {
        return messages.stream()
            .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
            .collect(Collectors.toList());
    }
}