package com.example.aigateway.param;

import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ParamNormalizer {

    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 2048;
    private static final double DEFAULT_TOP_P = 1.0;
    private static final double DEFAULT_PRESENCE_PENALTY = 0.0;
    private static final double DEFAULT_FREQUENCY_PENALTY = 0.0;
    private static final int DEFAULT_N = 1;

    public ModelRequest normalize(ModelRequest request, ModelType targetType) {
        return ModelRequest.builder()
            .modelType(request.getModelType())
            .model(request.getModel())
            .messages(request.getMessages())
            .temperature(normalizeTemperature(request.getTemperature(), targetType))
            .maxTokens(normalizeMaxTokens(request.getMaxTokens()))
            .topP(normalizeTopP(request.getTopP()))
            .presencePenalty(normalizePresencePenalty(request.getPresencePenalty()))
            .frequencyPenalty(normalizeFrequencyPenalty(request.getFrequencyPenalty()))
            .n(normalizeN(request.getN()))
            .stream(request.getStream())
            .stop(normalizeStop(request.getStop()))
            .system(request.getSystem())
            .tools(request.getTools())
            .toolChoice(request.getToolChoice())
            .extras(request.getExtras())
            .build();
    }

    private Double normalizeTemperature(Double temperature, ModelType targetType) {
        if (temperature == null) {
            return DEFAULT_TEMPERATURE;
        }
        temperature = clamp(temperature, 0.0, 2.0);
        return switch (targetType) {
            case OPENAI -> temperature;
            case CLAUDE -> clamp(temperature, 0.0, 1.0);
            case DEEPSEEK, TONGYI -> temperature;
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

    private Double normalizePresencePenalty(Double presencePenalty) {
        if (presencePenalty == null) {
            return DEFAULT_PRESENCE_PENALTY;
        }
        return clamp(presencePenalty, -2.0, 2.0);
    }

    private Double normalizeFrequencyPenalty(Double frequencyPenalty) {
        if (frequencyPenalty == null) {
            return DEFAULT_FREQUENCY_PENALTY;
        }
        return clamp(frequencyPenalty, -2.0, 2.0);
    }

    private Integer normalizeN(Integer n) {
        if (n == null) {
            return DEFAULT_N;
        }
        return Math.max(1, Math.min(n, 128));
    }

    private List<String> normalizeStop(List<String> stop) {
        return stop;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}