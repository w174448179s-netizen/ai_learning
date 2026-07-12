package com.example.aigateway.filter;

import com.example.aigateway.config.AuthConfig;
import com.example.aigateway.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements WebFilter {

    private static final String JWT_HEADER = "Authorization";
    private static final String JWT_PREFIX = "Bearer ";

    private final AuthConfig authConfig;
    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!isJwtAuthEnabled()) {
            return chain.filter(exchange);
        }

        String requestPath = exchange.getRequest().getPath().value();
        
        if (isPublicEndpoint(requestPath)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(JWT_HEADER);

        if (authHeader == null || authHeader.isEmpty()) {
            log.warn("Request without JWT token: {}", requestPath);
            return unauthorized(exchange, "Authentication token is required");
        }

        if (!authHeader.startsWith(JWT_PREFIX)) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(JWT_PREFIX.length());

        if (!jwtUtil.validateToken(token)) {
            log.warn("Invalid JWT token for path: {}", requestPath);
            return forbidden(exchange, "Invalid or expired token");
        }

        String clientId = jwtUtil.extractClientId(token);
        exchange.getRequest().mutate().header("X-Client-Id", clientId);
        log.debug("JWT token validated successfully for client: {}", clientId);

        return chain.filter(exchange);
    }

    private boolean isJwtAuthEnabled() {
        return authConfig.getJwt().isEnabled() &&
               (authConfig.getMode() == AuthConfig.AuthMode.SSO ||
                authConfig.getMode() == AuthConfig.AuthMode.BOTH);
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