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

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        Map<String, Object> body = buildRequestBody(request);
        
        return webClient.post()
            .uri("/chat/completions")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(this::parseResponse);
    }

    @Override
    protected Flux<String> doStreamChat(ModelRequest request) {
        Map<String, Object> body = buildRequestBody(request);
        body.put("stream", true);
        
        return webClient.post()
            .uri("/chat/completions")
            .bodyValue(body)
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

    private Map<String, Object> buildRequestBody(ModelRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("messages", convertMessages(request.getMessages()));
        body.put("temperature", request.getTemperature());
        body.put("max_tokens", request.getMaxTokens());
        body.put("top_p", request.getTopP());
        
        if (request.getPresencePenalty() != null && request.getPresencePenalty() != 0.0) {
            body.put("presence_penalty", request.getPresencePenalty());
        }
        if (request.getFrequencyPenalty() != null && request.getFrequencyPenalty() != 0.0) {
            body.put("frequency_penalty", request.getFrequencyPenalty());
        }
        if (request.getN() != null && request.getN() != 1) {
            body.put("n", request.getN());
        }
        if (request.getStop() != null && !request.getStop().isEmpty()) {
            body.put("stop", request.getStop());
        }
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", convertTools(request.getTools()));
        }
        if (request.getToolChoice() != null) {
            body.put("tool_choice", request.getToolChoice());
        }
        
        return body;
    }

    private ModelResponse parseResponse(JsonNode response) {
        String model = response.get("model").asText();
        JsonNode choices = response.get("choices").get(0);
        String content = choices.get("message").get("content").asText();
        String finishReason = choices.has("finish_reason") ? choices.get("finish_reason").asText() : null;
        
        List<ModelResponse.ToolCall> toolCalls = new ArrayList<>();
        if (choices.get("message").has("tool_calls")) {
            JsonNode toolCallsNode = choices.get("message").get("tool_calls");
            for (JsonNode toolCallNode : toolCallsNode) {
                ModelResponse.ToolCall toolCall = ModelResponse.ToolCall.builder()
                    .id(toolCallNode.get("id").asText())
                    .type("function")
                    .toolName(toolCallNode.get("function").get("name").asText())
                    .arguments(parseArguments(toolCallNode.get("function").get("arguments")))
                    .build();
                toolCalls.add(toolCall);
            }
        }
        
        JsonNode usage = response.get("usage");
        
        return ModelResponse.builder()
            .content(content)
            .model(model)
            .promptTokens(usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0)
            .completionTokens(usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0)
            .totalTokens(usage.has("total_tokens") ? usage.get("total_tokens").asInt() : 0)
            .finishReason(finishReason)
            .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
            .rawResponse(Map.of("response", response))
            .build();
    }

    private Map<String, Object> parseArguments(JsonNode argumentsNode) {
        try {
            return objectMapper.convertValue(argumentsNode, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("Failed to parse tool arguments: {}", e.getMessage());
            return Map.of();
        }
    }

    private Flux<String> parseStreamChunk(String jsonString) {
        try {
            JsonNode node = objectMapper.readTree(jsonString);
            JsonNode choices = node.get("choices");
            if (choices != null && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).get("delta");
                if (delta != null) {
                    if (delta.has("content")) {
                        String content = delta.get("content").asText();
                        if (!content.isEmpty()) {
                            return Flux.just(content);
                        }
                    }
                    if (delta.has("tool_calls")) {
                        return Flux.just(delta.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse stream chunk: {}", e.getMessage());
        }
        return Flux.empty();
    }

    private List<Map<String, Object>> convertMessages(List<Message> messages) {
        return messages.stream()
            .map(this::convertMessage)
            .collect(Collectors.toList());
    }

    private Map<String, Object> convertMessage(Message message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", message.getRole());
        
        if (message.getContent() != null && !message.getContent().isEmpty()) {
            result.put("content", message.getContent());
        } else if (message.getContentParts() != null && !message.getContentParts().isEmpty()) {
            result.put("content", convertContentParts(message.getContentParts()));
        }
        
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            result.put("tool_calls", convertToolCalls(message.getToolCalls()));
        }
        
        return result;
    }

    private List<Map<String, Object>> convertContentParts(List<Message.ContentPart> contentParts) {
        return contentParts.stream()
            .map(part -> {
                Map<String, Object> partMap = new LinkedHashMap<>();
                partMap.put("type", part.getType());
                if ("text".equals(part.getType())) {
                    partMap.put("text", part.getText());
                } else if ("image_url".equals(part.getType()) || "image".equals(part.getType())) {
                    if (part.getImage() != null) {
                        Map<String, Object> imageMap = new LinkedHashMap<>();
                        if (part.getImage().getUrl() != null) {
                            imageMap.put("url", part.getImage().getUrl());
                        } else if (part.getImage().getBase64() != null) {
                            imageMap.put("url", "data:image/" + part.getImage().getFormat() + ";base64," + part.getImage().getBase64());
                        }
                        partMap.put("image_url", imageMap);
                    }
                }
                return partMap;
            })
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> convertToolCalls(List<ModelResponse.ToolCall> toolCalls) {
        return toolCalls.stream()
            .map(tc -> Map.of(
                "id", tc.getId(),
                "type", tc.getType(),
                "function", Map.of(
                    "name", tc.getToolName(),
                    "arguments", tc.getArguments()
                )
            ))
            .collect(Collectors.toList());
    }

    private List<Map<String, Object>> convertTools(List<ModelRequest.Tool> tools) {
        return tools.stream()
            .map(tool -> {
                Map<String, Object> toolMap = new LinkedHashMap<>();
                toolMap.put("type", tool.getType());
                
                Map<String, Object> functionMap = new LinkedHashMap<>();
                functionMap.put("name", tool.getName());
                if (tool.getDescription() != null) {
                    functionMap.put("description", tool.getDescription());
                }
                if (tool.getParameters() != null) {
                    functionMap.put("parameters", tool.getParameters());
                }
                
                toolMap.put("function", functionMap);
                return toolMap;
            })
            .collect(Collectors.toList());
    }
}