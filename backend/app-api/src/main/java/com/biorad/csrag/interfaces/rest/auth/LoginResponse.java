package com.biorad.csrag.interfaces.rest.auth;

import java.util.Set;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserInfo user
) {
    public record UserInfo(
            String id,
            String username,
            String displayName,
            String email,
            Set<String> roles
    ) {}
}
