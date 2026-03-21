package com.dazzle.asklepios.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Configuration
public class SetupServiceFeignConfig {

    @Bean
    public RequestInterceptor authHeaderForwardingInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                ServletRequestAttributes attrs =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

                if (attrs == null) {
                    return;
                }

                HttpServletRequest request = attrs.getRequest();
                String authHeader = request.getHeader(AUTHORIZATION);

                if (StringUtils.hasText(authHeader)) {
                    template.header(AUTHORIZATION, authHeader);
                }
            }
        };
    }
}
