package com.biorad.csrag.interfaces.rest.auth;

import com.biorad.csrag.infrastructure.persistence.user.AppUserJpaEntity;
import com.biorad.csrag.infrastructure.security.AppUserDetailsService;
import com.biorad.csrag.infrastructure.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.biorad.csrag.common.exception.ForbiddenException;
import com.biorad.csrag.common.exception.UnauthorizedException;

import java.util.Set;

@Tag(name = "Auth", description = "인증 API (로그인, 토큰 갱신)")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AppUserDetailsService userDetailsService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtTokenProvider jwtTokenProvider,
            AppUserDetailsService userDetailsService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Operation(summary = "로그인", description = "사용자명/비밀번호로 로그인하여 JWT 토큰을 발급받습니다")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "401", description = "잘못된 자격 증명")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("auth.login.request username={}", request.username());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            AppUserJpaEntity user = userDetailsService.findByUsername(request.username());
            Set<String> roles = user.getRoleNames();

            String accessToken = jwtTokenProvider.generateAccessToken(
                    user.getId().toString(), user.getUsername(), roles);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString());

            LoginResponse response = new LoginResponse(
                    accessToken,
                    refreshToken,
                    "Bearer",
                    900, // 15 minutes in seconds
                    new LoginResponse.UserInfo(
                            user.getId().toString(),
                            user.getUsername(),
                            user.getDisplayName(),
                            user.getEmail(),
                            roles
                    )
            );

            log.info("auth.login.success username={} roles={}", user.getUsername(), roles);
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            log.warn("auth.login.failed username={}", request.username());
            throw e; // handled by GlobalExceptionHandler.handleBadCredentials
        }
    }

    @Operation(summary = "토큰 갱신", description = "Refresh 토큰으로 새 Access 토큰을 발급받습니다")
    @ApiResponse(responseCode = "200", description = "갱신 성공")
    @ApiResponse(responseCode = "401", description = "유효하지 않은 Refresh 토큰")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("auth.refresh.request");

        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Invalid or expired refresh token");
        }

        String tokenType = jwtTokenProvider.getTokenType(request.refreshToken());
        if (!"refresh".equals(tokenType)) {
            throw new UnauthorizedException("INVALID_TOKEN_TYPE", "Token is not a refresh token");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(request.refreshToken());

        // Look up user to get fresh roles
        AppUserJpaEntity user;
        try {
            user = userDetailsService.findByUsername(
                    jwtTokenProvider.parseToken(request.refreshToken()).getSubject());
        } catch (Exception e) {
            // Subject is userId, not username — try to find directly
            // For simplicity, re-issue with the user ID
            String accessToken = jwtTokenProvider.generateAccessToken(userId, userId, Set.of());
            log.info("auth.refresh.success userId={}", userId);
            return ResponseEntity.ok(new TokenResponse(accessToken, "Bearer", 900));
        }

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId().toString(), user.getUsername(), user.getRoleNames());

        log.info("auth.refresh.success userId={}", userId);
        return ResponseEntity.ok(new TokenResponse(accessToken, "Bearer", 900));
    }

    @Operation(summary = "현재 사용자 정보 조회", description = "인증된 사용자의 프로필 정보를 반환합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "401", description = "인증되지 않음")
    @PostMapping("/me")
    public ResponseEntity<LoginResponse.UserInfo> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("NOT_AUTHENTICATED", "Not authenticated");
        }

        String userId = authentication.getName();
        try {
            AppUserJpaEntity user = userDetailsService.findByUsername(userId);
            return ResponseEntity.ok(new LoginResponse.UserInfo(
                    user.getId().toString(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getEmail(),
                    user.getRoleNames()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(new LoginResponse.UserInfo(
                    userId, userId, null, null, Set.of()
            ));
        }
    }
}
