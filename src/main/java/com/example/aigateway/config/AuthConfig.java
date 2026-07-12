package com.example.aigateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "gateway.auth")
public class AuthConfig {

    private AuthMode mode = AuthMode.SSO;
    private String apiKeys;
    private JwtConfig jwt = new JwtConfig();

    public enum AuthMode {
        SSO,
        API_KEY,
        BOTH,
        NONE
    }

    @Data
    public static class JwtConfig {
        private boolean enabled = true;
        private String secret;
        private long expiryHours = 24;
        private String ssoJwksUrl;
        private String ssoIssuer;
    }
}