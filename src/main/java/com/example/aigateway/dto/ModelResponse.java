package com.example.aigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelResponse {

    private String content;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Long latencyMs;

    private String finishReason;

    private List<ToolCall> toolCalls;

    private Map<String, Object> rawResponse;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;

        private String type;

        private String toolName;

        private Map<String, Object> arguments;

        private String result;
    }
}