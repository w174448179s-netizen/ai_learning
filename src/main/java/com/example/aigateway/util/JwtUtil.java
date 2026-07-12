package com.example.aigateway.util;

import com.example.aigateway.config.AuthConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final AuthConfig authConfig;

    private SecretKey getSigningKey() {
        String jwtSecret = authConfig.getJwt().getSecret();
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String clientId, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(authConfig.getJwt().getExpiryHours() * 3600);

        return Jwts.builder()
            .claims(extraClaims)
            .subject(clientId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(getSigningKey())
            .compact();
    }

    public String generateToken(String clientId) {
        return generateToken(clientId, Map.of());
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            
            if (isTokenExpired(token)) {
                log.warn("JWT token expired");
                return false;
            }
            
            String ssoIssuer = authConfig.getJwt().getSsoIssuer();
            if (ssoIssuer != null && !ssoIssuer.isEmpty()) {
                String tokenIssuer = claims.getIssuer();
                if (!ssoIssuer.equals(tokenIssuer)) {
                    log.warn("Invalid JWT issuer: expected {}, got {}", ssoIssuer, tokenIssuer);
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public String extractClientId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = parseToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims parseToken(String token) {
        String ssoJwksUrl = authConfig.getJwt().getSsoJwksUrl();
        
        if (ssoJwksUrl != null && !ssoJwksUrl.isEmpty()) {
            return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } else {
            return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}