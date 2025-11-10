package com.dazzle.asklepios.config;

import com.dazzle.asklepios.config.openapi.CustomOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile(Constants.SPRING_PROFILE_API_DOCS)
public class OpenApiConfiguration {

    public static final String API_FIRST_PACKAGE = "com.dazzle.asklepios.web.api";

    @Bean
    @ConditionalOnMissingBean(name = "apiFirstGroupedOpenAPI")
    public GroupedOpenApi apiFirstGroupedOpenAPI(
        CustomOpenApiCustomizer customOpenApiCustomizer
    ) {
        return GroupedOpenApi.builder()
            .group("openapi")
            .addOpenApiCustomizer(customOpenApiCustomizer)
            .packagesToScan(API_FIRST_PACKAGE)
            .pathsToMatch("/api/**")
            .build();
    }
}
