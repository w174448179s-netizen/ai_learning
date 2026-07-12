package com.example.aigateway.param;

import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelType;
import org.springframework.stereotype.Component;

@Component
public class ParamNormalizer {

    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 2048;
    private static final double DEFAULT_TOP_P = 0.9;

    public ModelRequest normalize(ModelRequest request, ModelType targetType) {
        return ModelRequest.builder()
            .modelType(request.getModelType())
            .model(request.getModel())
            .messages(request.getMessages())
            .temperature(normalizeTemperature(request.getTemperature(), targetType))
            .maxTokens(normalizeMaxTokens(request.getMaxTokens()))
            .topP(normalizeTopP(request.getTopP()))
            .stream(request.getStream())
            .extras(request.getExtras())
            .build();
    }

    private Double normalizeTemperature(Double temperature, ModelType targetType) {
        if (temperature == null) {
            return DEFAULT_TEMPERATURE;
        }

        temperature = clamp(temperature, 0.0, 1.0);

        return switch (targetType) {
            case OPENAI -> temperature * 2.0;
            case CLAUDE, DEEPSEEK, TONGYI -> temperature;
        };
    }

    private Integer normalizeMaxTokens(Integer maxTokens) {
        if (maxTokens == null) {
            return DEFAULT_MAX_TOKENS;
        }
        return Math.max(1, Math.min(maxTokens, 32768));
    }

    private Double normalizeTopP(Double topP) {
        if (topP == null) {
            return DEFAULT_TOP_P;
        }
        return clamp(topP, 0.0, 1.0);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}