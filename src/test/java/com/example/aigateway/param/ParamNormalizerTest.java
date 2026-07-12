package com.example.aigateway.param;

import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParamNormalizerTest {

    private ParamNormalizer paramNormalizer;

    @BeforeEach
    void setUp() {
        paramNormalizer = new ParamNormalizer();
    }

    @Test
    void testTemperatureNormalizationForOpenAI() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .temperature(0.5)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.OPENAI);

        assertEquals(1.0, normalized.getTemperature());
    }

    @Test
    void testTemperatureNormalizationForClaude() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.CLAUDE)
            .temperature(0.7)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.CLAUDE);

        assertEquals(0.7, normalized.getTemperature());
    }

    @Test
    void testTemperatureNormalizationForDeepSeek() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.DEEPSEEK)
            .temperature(0.3)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.DEEPSEEK);

        assertEquals(0.3, normalized.getTemperature());
    }

    @Test
    void testTemperatureClamping() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.CLAUDE)
            .temperature(1.5)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.CLAUDE);

        assertEquals(1.0, normalized.getTemperature());
    }

    @Test
    void testDefaultTemperature() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.OPENAI);

        assertEquals(1.4, normalized.getTemperature());
    }

    @Test
    void testMaxTokensNormalization() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .maxTokens(50000)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.OPENAI);

        assertEquals(32768, normalized.getMaxTokens());
    }

    @Test
    void testDefaultMaxTokens() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.OPENAI);

        assertEquals(2048, normalized.getMaxTokens());
    }

    @Test
    void testTopPNormalization() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .topP(1.5)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.OPENAI);

        assertEquals(1.0, normalized.getTopP());
    }

    @Test
    void testDefaultTopP() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.OPENAI);

        assertEquals(0.9, normalized.getTopP());
    }
}