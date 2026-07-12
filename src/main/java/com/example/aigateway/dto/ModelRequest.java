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
public class ModelRequest {
    private ModelType modelType;
    private String model;
    private List<Message> messages;
    private Double temperature;
    private Integer maxTokens;
    private Double topP;
    private Boolean stream;
    private Map<String, Object> extras;
}