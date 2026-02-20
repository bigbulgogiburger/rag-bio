package com.biorad.csrag.interfaces.rest.auth;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {}
