package com.example.aigateway.dto;

import jakarta.validation.constraints.NotBlank;
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
public class Message {

    @NotBlank(message = "Role is required")
    private String role;

    private String content;

    private List<ContentPart> contentParts;

    private List<ModelResponse.ToolCall> toolCalls;

    private String toolCallId;

    private String toolResult;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContentPart {
        @NotBlank(message = "Content part type is required")
        private String type;

        private String text;

        private ImageContent image;

        private Map<String, Object> data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageContent {
        private String url;

        private String base64;

        private String format;
    }
}