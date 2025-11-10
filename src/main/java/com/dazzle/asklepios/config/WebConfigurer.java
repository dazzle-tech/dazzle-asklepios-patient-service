package com.dazzle.asklepios.config;

import jakarta.servlet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.*;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * Configuration of web application with Servlet 3.0 APIs.
 */
@Configuration
public class WebConfigurer implements ServletContextInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(WebConfigurer.class);

    private final Environment env;

    private final CorsConfigProperties corsConfigProperties;


    public WebConfigurer(Environment env, CorsConfigProperties corsConfigProperties) {
        this.env = env;
        this.corsConfigProperties = corsConfigProperties;
    }

    @Override
    public void onStartup(ServletContext servletContext) {
        if (env.getActiveProfiles().length != 0) {
            LOG.info("Web application configuration, using profiles: {}", (Object[]) env.getActiveProfiles());
        }

        LOG.info("Web application fully configured");
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        if (StringUtils.hasText(corsConfigProperties.getAllowedOrigins()) ||
            StringUtils.hasText(corsConfigProperties.getAllowedOriginPatterns())) {

            if (StringUtils.hasText(corsConfigProperties.getAllowedOrigins())) {
                config.setAllowedOrigins(Arrays.asList(corsConfigProperties.getAllowedOrigins().split(",")));
            }

            if (StringUtils.hasText(corsConfigProperties.getAllowedOriginPatterns())) {
                config.setAllowedOriginPatterns(Arrays.asList(corsConfigProperties.getAllowedOriginPatterns().split(",")));
            }

            config.setAllowedMethods(Arrays.asList(corsConfigProperties.getAllowedMethods().split(",")));
            config.setAllowedHeaders(Arrays.asList(corsConfigProperties.getAllowedHeaders().split(",")));
            config.setExposedHeaders(Arrays.asList(corsConfigProperties.getExposedHeaders().split(",")));
            config.setAllowCredentials(corsConfigProperties.isAllowCredentials());
            config.setMaxAge((long) corsConfigProperties.getMaxAge());

            if (!CollectionUtils.isEmpty(config.getAllowedOrigins()) || !CollectionUtils.isEmpty(config.getAllowedOriginPatterns())) {
                LOG.debug("Registering CORS filter");
                source.registerCorsConfiguration("/api/**", config);
                source.registerCorsConfiguration("/management/**", config);
                source.registerCorsConfiguration("/v3/api-docs", config);
                source.registerCorsConfiguration("/swagger-ui/**", config);
            }
        }
            return new CorsFilter(source);
        }
    }

