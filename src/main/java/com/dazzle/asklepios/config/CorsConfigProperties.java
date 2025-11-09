package com.dazzle.asklepios.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "patient.cors")
public class CorsConfigProperties {

    private String allowedOrigins;
    private String allowedOriginPatterns;
    private String allowedMethods;
    private String allowedHeaders;
    private String exposedHeaders;
    private boolean allowCredentials;
    private int maxAge;

}
