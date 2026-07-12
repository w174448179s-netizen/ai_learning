package com.example.aigateway.filter;

import com.example.aigateway.config.AuthConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter implements WebFilter {

    private static final String API_KEY_HEADER = "Authorization";
    private static final String API_KEY_PREFIX = "Bearer ";

    private final AuthConfig authConfig;

    private final Set<String> validKeys = new HashSet<>();
    private final Map<String, Instant> keyExpiryMap = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!isApiKeyAuthEnabled()) {
            return chain.filter(exchange);
        }

        if (validKeys.isEmpty()) {
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
            return chain.filter(exchange);
        }

        String apiKey = authHeader.substring(API_KEY_PREFIX.length());

        if (isKeyExpired(apiKey)) {
            log.warn("API key expired: {} path: {}", maskApiKey(apiKey), requestPath);
            return unauthorized(exchange, "API key has expired");
        }

        if (!validKeys.contains(apiKey)) {
            log.warn("Invalid API key: {} path: {}", maskApiKey(apiKey), requestPath);
            return forbidden(exchange, "Invalid API key");
        }

        log.debug("API key validated successfully: {} path: {}", maskApiKey(apiKey), requestPath);
        return chain.filter(exchange);
    }

    private boolean isApiKeyAuthEnabled() {
        return authConfig.getMode() == AuthConfig.AuthMode.API_KEY ||
               authConfig.getMode() == AuthConfig.AuthMode.BOTH;
    }

    private void initValidKeys() {
        validKeys.clear();
        keyExpiryMap.clear();
        
        String apiKeys = authConfig.getApiKeys();
        if (apiKeys != null && !apiKeys.isEmpty()) {
            for (String keyEntry : apiKeys.split(",")) {
                String[] parts = keyEntry.trim().split(":");
                String key = parts[0].trim();
                validKeys.add(key);
                
                if (parts.length > 1) {
                    try {
                        long expirySeconds = Long.parseLong(parts[1].trim());
                        keyExpiryMap.put(key, Instant.now().plusSeconds(expirySeconds));
                        log.info("Loaded API key with expiry: {} -> {}", maskApiKey(key), keyExpiryMap.get(key));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid expiry format for key: {}, ignoring expiry", maskApiKey(key));
                    }
                }
            }
        }
        log.info("Loaded {} valid API keys", validKeys.size());
    }

    private boolean isKeyExpired(String apiKey) {
        Instant expiry = keyExpiryMap.get(apiKey);
        if (expiry == null) {
            return false;
        }
        return Instant.now().isAfter(expiry);
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 4) {
            return "****";
        }
        return "****" + apiKey.substring(apiKey.length() - 4);
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