package com.biorad.csrag.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpiration,
        long refreshTokenExpiration
) {
    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            secret = "default-dev-secret-key-change-in-production-minimum-32-chars!!";
        }
        if (accessTokenExpiration <= 0) {
            accessTokenExpiration = 900000; // 15 minutes
        }
        if (refreshTokenExpiration <= 0) {
            refreshTokenExpiration = 604800000; // 7 days
        }
    }
}
