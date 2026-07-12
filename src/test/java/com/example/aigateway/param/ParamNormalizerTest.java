package com.example.aigateway.param;

import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void testTemperatureNormalizationForTongYi() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.TONGYI)
            .temperature(0.9)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.TONGYI);

        assertEquals(0.9, normalized.getTemperature());
    }

    @Test
    void testTemperatureClampingUpper() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.CLAUDE)
            .temperature(1.5)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.CLAUDE);

        assertEquals(1.0, normalized.getTemperature());
    }

    @Test
    void testTemperatureClampingLower() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .temperature(-0.5)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.OPENAI);

        assertEquals(0.0, normalized.getTemperature());
    }

    @Test
    void testDefaultTemperature() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.OPENAI);

        assertEquals(0.7, normalized.getTemperature());
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
    void testMaxTokensClampingLower() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .maxTokens(-100)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.OPENAI);

        assertEquals(1, normalized.getMaxTokens());
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
    void testTopPNormalizationUpper() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .topP(1.5)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.OPENAI);

        assertEquals(1.0, normalized.getTopP());
    }

    @Test
    void testTopPNormalizationLower() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .topP(-0.5)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.OPENAI);

        assertEquals(0.0, normalized.getTopP());
    }

    @Test
    void testDefaultTopP() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.OPENAI);

        assertEquals(0.9, normalized.getTopP());
    }

    @Test
    void testFullNormalization() {
        ModelRequest request = ModelRequest.builder()
            .modelType(ModelType.OPENAI)
            .model("gpt-3.5-turbo")
            .temperature(0.5)
            .maxTokens(1000)
            .topP(0.8)
            .build();

        ModelRequest normalized = paramNormalizer.normalize(request, ModelType.OPENAI);

        assertEquals(ModelType.OPENAI, normalized.getModelType());
        assertEquals("gpt-3.5-turbo", normalized.getModel());
        assertEquals(1.0, normalized.getTemperature());
        assertEquals(1000, normalized.getMaxTokens());
        assertEquals(0.8, normalized.getTopP());
    }

    @Test
    void testNullRequest() {
        assertThrows(NullPointerException.class, () -> 
            paramNormalizer.normalize(null, ModelType.OPENAI)
        );
    }
}