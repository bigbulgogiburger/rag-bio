package com.biorad.csrag.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is now managed by Spring Security's CorsConfigurationSource in SecurityConfig.
 * This class is kept as a no-op WebMvcConfigurer to avoid breaking component scan expectations.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
}
