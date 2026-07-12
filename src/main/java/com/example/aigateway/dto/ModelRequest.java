package com.example.aigateway.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class ModelRequest {

    @NotNull(message = "Model type is required")
    private ModelType modelType;

    @NotBlank(message = "Model name is required")
    private String model;

    @NotEmpty(message = "Messages cannot be empty")
    private List<Message> messages;

    @Min(value = 0, message = "Temperature must be at least 0")
    @Max(value = 2, message = "Temperature must be at most 2")
    @Builder.Default
    private Double temperature = 0.7;

    @Min(value = 1, message = "Max tokens must be at least 1")
    @Max(value = 32768, message = "Max tokens must be at most 32768")
    @Builder.Default
    private Integer maxTokens = 2048;

    @Min(value = 0, message = "Top P must be at least 0")
    @Max(value = 1, message = "Top P must be at most 1")
    @Builder.Default
    private Double topP = 1.0;

    @Min(value = -2, message = "Presence penalty must be at least -2")
    @Max(value = 2, message = "Presence penalty must be at most 2")
    @Builder.Default
    private Double presencePenalty = 0.0;

    @Min(value = -2, message = "Frequency penalty must be at least -2")
    @Max(value = 2, message = "Frequency penalty must be at most 2")
    @Builder.Default
    private Double frequencyPenalty = 0.0;

    @Min(value = 1, message = "N must be at least 1")
    @Max(value = 128, message = "N must be at most 128")
    @Builder.Default
    private Integer n = 1;

    @Builder.Default
    private Boolean stream = false;

    private List<String> stop;

    private String system;

    private List<Tool> tools;

    private String toolChoice;

    private Map<String, Object> extras;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tool {
        @NotBlank(message = "Tool type is required")
        private String type;

        @NotBlank(message = "Tool name is required")
        private String name;

        private String description;

        private Map<String, Object> parameters;
    }
}