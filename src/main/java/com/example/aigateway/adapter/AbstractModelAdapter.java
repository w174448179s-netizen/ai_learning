package com.example.aigateway.adapter;

import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelResponse;
import com.example.aigateway.dto.ModelType;
import com.example.aigateway.param.ParamNormalizer;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public abstract class AbstractModelAdapter implements ModelAdapter {

    protected final ParamNormalizer paramNormalizer;
    protected final ModelType modelType;

    protected AbstractModelAdapter(ParamNormalizer paramNormalizer, ModelType modelType) {
        this.paramNormalizer = paramNormalizer;
        this.modelType = modelType;
    }

    @Override
    public Mono<ModelResponse> chat(ModelRequest request) {
        long startTime = System.currentTimeMillis();
        ModelRequest normalizedRequest = paramNormalizer.normalize(request, modelType);
        
        return doChat(normalizedRequest)
            .doOnNext(response -> response.setLatencyMs(System.currentTimeMillis() - startTime))
            .onErrorMap(ex -> {
                log.error("Chat failed for model type {}: {}", modelType, ex.getMessage(), ex);
                return new RuntimeException(ex);
            });
    }

    @Override
    public Flux<String> streamChat(ModelRequest request) {
        ModelRequest normalizedRequest = paramNormalizer.normalize(request, modelType);
        return doStreamChat(normalizedRequest)
            .onErrorContinue((ex, obj) -> 
                log.error("Stream chat error for model type {}: {}", modelType, ex.getMessage())
            );
    }

    @Override
    public boolean supports(ModelType modelType) {
        return this.modelType == modelType;
    }

    protected abstract Mono<ModelResponse> doChat(ModelRequest request);

    protected abstract Flux<String> doStreamChat(ModelRequest request);
}