package com.example.aigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class ApiKeyAuthFilter implements WebFilter {

    private static final String API_KEY_HEADER = "Authorization";
    private static final String API_KEY_PREFIX = "Bearer ";

    @Value("${gateway.api-keys:sk-demo-key}")
    private String apiKeys;

    private Set<String> validKeys;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (validKeys == null) {
            initValidKeys();
        }

        String requestPath = exchange.getRequest().getPath().value();
        if (isPublicEndpoint(requestPath)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);

        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("Request without API key: {}", requestPath);
            return unauthorized(exchange, "API key is required");
        }

        if (!authHeader.startsWith(API_KEY_PREFIX)) {
            log.warn("Invalid API key format: {}", requestPath);
            return unauthorized(exchange, "Invalid API key format");
        }

        String apiKey = authHeader.substring(API_KEY_PREFIX.length());

        if (!validKeys.contains(apiKey)) {
            log.warn("Invalid API key: {}", requestPath);
            return forbidden(exchange, "Invalid API key");
        }

        return chain.filter(exchange);
    }

    private void initValidKeys() {
        validKeys = new HashSet<>();
        if (apiKeys != null && !apiKeys.isEmpty()) {
            for (String key : apiKeys.split(",")) {
                validKeys.add(key.trim());
            }
        }
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/v1/health") || 
               path.startsWith("/api/v1/models");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String body = "{\"error\": \"Unauthorized\", \"message\": \"" + message + "\"}";
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes())));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        String body = "{\"error\": \"Forbidden\", \"message\": \"" + message + "\"}";
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes())));
    }
}