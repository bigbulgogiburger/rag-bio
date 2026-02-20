package com.biorad.csrag.testutil;

import com.biorad.csrag.infrastructure.security.RateLimitFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Test configuration that replaces the RateLimitFilter with a pass-through
 * version to prevent 429 errors during integration tests.
 * Also disables servlet-container auto-registration of all RateLimitFilter beans.
 */
@Configuration
public class TestRateLimitConfig {

    @Bean
    @Primary
    public RateLimitFilter testRateLimitFilter() {
        return new RateLimitFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                filterChain.doFilter(request, response);
            }
        };
    }

    /** Disable auto-registration of the @Primary test filter */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> testRateLimitFilterRegistration(
            @Qualifier("testRateLimitFilter") RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    /** Disable auto-registration of the original @Component RateLimitFilter */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> originalRateLimitFilterRegistration(
            @Qualifier("rateLimitFilter") RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }
}
