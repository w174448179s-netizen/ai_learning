package com.example.aigateway.adapter;

import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelResponse;
import com.example.aigateway.dto.ModelType;
import com.example.aigateway.dto.Message;
import com.example.aigateway.param.ParamNormalizer;
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
        Map<String, Object> body = buildRequestBody(request);
        
        return webClient.post()
            .uri("/messages")
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
            .uri("/messages")
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
        body.put("max_tokens", request.getMaxTokens());
        body.put("temperature", request.getTemperature());
        
        if (request.getTopP() != null && request.getTopP() != 1.0) {
            body.put("top_p", request.getTopP());
        }
        
        List<Map<String, Object>> messages = new ArrayList<>();
        
        if (request.getSystem() != null && !request.getSystem().isEmpty()) {
            body.put("system", request.getSystem());
        }
        
        messages.addAll(convertMessages(request.getMessages()));
        body.put("messages", messages);
        
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
        String finishReason = response.has("stop_reason") ? response.get("stop_reason").asText() : null;
        
        String text = "";
        List<ModelResponse.ToolCall> toolCalls = new ArrayList<>();
        
        JsonNode content = response.get("content");
        if (content != null && content.isArray()) {
            for (JsonNode contentNode : content) {
                String type = contentNode.get("type").asText();
                if ("text".equals(type)) {
                    text += contentNode.get("text").asText();
                } else if ("tool_use".equals(type)) {
                    ModelResponse.ToolCall toolCall = ModelResponse.ToolCall.builder()
                        .id(contentNode.get("id").asText())
                        .type("function")
                        .toolName(contentNode.get("name").asText())
                        .arguments(parseArguments(contentNode.get("input")))
                        .build();
                    toolCalls.add(toolCall);
                }
            }
        }
        
        JsonNode usage = response.get("usage");
        
        return ModelResponse.builder()
            .content(text)
            .model(model)
            .promptTokens(usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0)
            .completionTokens(usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0)
            .totalTokens(usage.has("input_tokens") && usage.has("output_tokens") 
                ? usage.get("input_tokens").asInt() + usage.get("output_tokens").asInt() : 0)
            .finishReason(finishReason)
            .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
            .rawResponse(Map.of("response", response))
            .build();
    }

    private Map<String, Object> parseArguments(JsonNode argumentsNode) {
        try {
            return objectMapper.convertValue(argumentsNode, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("Failed to parse tool arguments: {}", e.getMessage());
            return Map.of();
        }
    }

    private Flux<String> parseStreamChunk(String jsonString) {
        try {
            JsonNode node = objectMapper.readTree(jsonString);
            JsonNode type = node.get("type");
            
            if (type != null) {
                switch (type.asText()) {
                    case "content_block_delta":
                        JsonNode delta = node.get("delta");
                        if (delta != null && delta.has("text")) {
                            String text = delta.get("text").asText();
                            if (!text.isEmpty()) {
                                return Flux.just(text);
                            }
                        }
                        break;
                    case "tool_use":
                        return Flux.just(node.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse Claude stream chunk: {}", e.getMessage());
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
                } else if ("image".equals(part.getType())) {
                    if (part.getImage() != null) {
                        Map<String, Object> imageMap = new LinkedHashMap<>();
                        if (part.getImage().getUrl() != null) {
                            imageMap.put("source", Map.of("type", "url", "url", part.getImage().getUrl()));
                        } else if (part.getImage().getBase64() != null) {
                            imageMap.put("source", Map.of("type", "base64", "media_type", "image/" + part.getImage().getFormat(), "data", part.getImage().getBase64()));
                        }
                        partMap.put("image", imageMap);
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
                toolMap.put("name", tool.getName());
                toolMap.put("description", tool.getDescription());
                if (tool.getParameters() != null) {
                    toolMap.put("input_schema", tool.getParameters());
                }
                return toolMap;
            })
            .collect(Collectors.toList());
    }
}