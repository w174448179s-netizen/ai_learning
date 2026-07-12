package com.example.aigateway.adapter;

import com.example.aigateway.dto.ModelRequest;
import com.example.aigateway.dto.ModelResponse;
import com.example.aigateway.dto.ModelType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ModelAdapter {
    Mono<ModelResponse> chat(ModelRequest request);

    Flux<String> streamChat(ModelRequest request);

    boolean supports(ModelType modelType);

    Mono<Boolean> healthCheck();
}