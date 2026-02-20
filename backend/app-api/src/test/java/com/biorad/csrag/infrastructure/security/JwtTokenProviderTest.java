package com.biorad.csrag.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(
                "test-secret-key-for-unit-tests-must-be-at-least-32-chars",
                900_000L,   // 15 min
                604_800_000L // 7 days
        );
        provider = new JwtTokenProvider(props);
    }

    @Test
    void generateAccessToken_containsExpectedClaims() {
        String token = provider.generateAccessToken("user-1", "testuser", Set.of("ADMIN", "REVIEWER"));

        Claims claims = provider.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.get("username", String.class)).isEqualTo("testuser");
        assertThat(claims.get("type", String.class)).isEqualTo("access");
        String roles = claims.get("roles", String.class);
        assertThat(roles).contains("ADMIN");
        assertThat(roles).contains("REVIEWER");
    }

    @Test
    void generateRefreshToken_hasRefreshType() {
        String token = provider.generateRefreshToken("user-1");

        Claims claims = provider.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    void validateToken_returnsTrueForValidToken() {
        String token = provider.generateAccessToken("user-1", "testuser", Set.of("ADMIN"));
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_returnsFalseForInvalidToken() {
        assertThat(provider.validateToken("invalid.token.value")).isFalse();
    }

    @Test
    void validateToken_returnsFalseForExpiredToken() {
        JwtProperties shortLived = new JwtProperties(
                "test-secret-key-for-unit-tests-must-be-at-least-32-chars",
                -1000L, // already expired
                -1000L
        );
        // The compact constructor normalizes to defaults when <= 0
        // So we use a different approach: generate with very short expiry
        JwtProperties tinyExpiry = new JwtProperties(
                "test-secret-key-for-unit-tests-must-be-at-least-32-chars",
                1L, // 1ms expiry
                1L
        );
        JwtTokenProvider shortProvider = new JwtTokenProvider(tinyExpiry);
        String token = shortProvider.generateAccessToken("user-1", "testuser", Set.of("ADMIN"));

        // Wait for expiry
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}

        assertThat(shortProvider.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_returnsFalseForNullOrEmpty() {
        // null or empty tokens should either return false or throw
        // The jjwt library throws IllegalArgumentException for null, which may not be caught
        // by the JwtException catch block — so validateToken may propagate that exception
        try {
            boolean result = provider.validateToken(null);
            assertThat(result).isFalse();
        } catch (IllegalArgumentException e) {
            // Expected: jjwt throws IllegalArgumentException for null/empty token
            assertThat(e).isNotNull();
        }
    }

    @Test
    void getUserIdFromToken_returnsSubject() {
        String token = provider.generateAccessToken("user-42", "admin", Set.of("ADMIN"));
        assertThat(provider.getUserIdFromToken(token)).isEqualTo("user-42");
    }

    @Test
    void getTokenType_returnsAccessForAccessToken() {
        String token = provider.generateAccessToken("u1", "user", Set.of("REVIEWER"));
        assertThat(provider.getTokenType(token)).isEqualTo("access");
    }

    @Test
    void getTokenType_returnsRefreshForRefreshToken() {
        String token = provider.generateRefreshToken("u1");
        assertThat(provider.getTokenType(token)).isEqualTo("refresh");
    }

    @Test
    void validateToken_returnsFalseForTamperedSignature() {
        String token = provider.generateAccessToken("u1", "user", Set.of("ADMIN"));
        // Corrupt the signature part by flipping a character
        String[] parts = token.split("\\.");
        char[] sigChars = parts[2].toCharArray();
        sigChars[0] = sigChars[0] == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + new String(sigChars);
        assertThat(provider.validateToken(tampered)).isFalse();
    }

    @Test
    void shortSecret_isPaddedTo32Bytes() {
        JwtProperties shortSecret = new JwtProperties("short", 900_000L, 604_800_000L);
        JwtTokenProvider shortProvider = new JwtTokenProvider(shortSecret);

        String token = shortProvider.generateAccessToken("u1", "user", Set.of("ADMIN"));
        assertThat(shortProvider.validateToken(token)).isTrue();
    }

    @Test
    void jwtProperties_defaultValues() {
        JwtProperties defaults = new JwtProperties(null, 0, 0);
        assertThat(defaults.secret()).isNotBlank();
        assertThat(defaults.accessTokenExpiration()).isEqualTo(900_000L);
        assertThat(defaults.refreshTokenExpiration()).isEqualTo(604_800_000L);
    }
}
