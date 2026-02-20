package com.biorad.csrag.infrastructure.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            try {
                Claims claims = jwtTokenProvider.parseToken(token);
                String tokenType = claims.get("type", String.class);

                if (!"access".equals(tokenType)) {
                    log.debug("jwt.filter.skipped reason=not-access-token type={}", tokenType);
                    filterChain.doFilter(request, response);
                    return;
                }

                String userId = claims.getSubject();
                String username = claims.get("username", String.class);
                String rolesStr = claims.get("roles", String.class);

                Set<SimpleGrantedAuthority> authorities = Arrays.stream(
                                (rolesStr == null ? "" : rolesStr).split(","))
                        .filter(r -> !r.isBlank())
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.trim()))
                        .collect(Collectors.toSet());

                JwtAuthenticationToken authToken = new JwtAuthenticationToken(
                        userId, username, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("jwt.filter.authenticated userId={} roles={}", userId, rolesStr);
            } catch (Exception e) {
                log.debug("jwt.filter.error reason={}", e.getMessage());
            }
        } else {
            // Backward compatibility: check X-User-Id / X-User-Roles headers
            String headerUserId = request.getHeader("X-User-Id");
            String headerRoles = request.getHeader("X-User-Roles");

            if (StringUtils.hasText(headerUserId)) {
                Set<SimpleGrantedAuthority> authorities = Arrays.stream(
                                (headerRoles == null ? "" : headerRoles).split(","))
                        .filter(r -> !r.isBlank())
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.trim().toUpperCase()))
                        .collect(Collectors.toSet());

                JwtAuthenticationToken authToken = new JwtAuthenticationToken(
                        headerUserId, headerUserId, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("jwt.filter.legacy-header userId={} roles={}", headerUserId, headerRoles);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
