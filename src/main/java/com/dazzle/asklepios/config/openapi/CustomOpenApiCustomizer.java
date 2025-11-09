package com.dazzle.asklepios.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class CustomOpenApiCustomizer implements OpenApiCustomizer, Ordered {
    private int order = 0;
    private final ApiDocs properties;

    public CustomOpenApiCustomizer(ApiDocs properties) {
        this.properties = properties;
    }

    @Override
    public void customise(OpenAPI openAPI) {
        Contact contactInfo = buildContact();
        Info apiInfo = buildApiInfo(contactInfo);
        openAPI.info(apiInfo);

        addServersToOpenAPI(openAPI);
    }

    private Contact buildContact() {
        return new Contact()
            .name(properties.getContactName())
            .url(properties.getContactUrl())
            .email(properties.getContactEmail());
    }

    private Info buildApiInfo(Contact contactInfo) {
        return new Info()
            .contact(contactInfo)
            .title(properties.getTitle())
            .description(properties.getDescription())
            .version(properties.getVersion())
            .termsOfService(properties.getTermsOfServiceUrl())
            .license(buildLicense());
    }

    private License buildLicense() {
        return new License()
            .name(properties.getLicense())
            .url(properties.getLicenseUrl());
    }

    private void addServersToOpenAPI(OpenAPI openAPI) {
        for (ApiDocs.Server server : properties.getServers()) {
            openAPI.addServersItem(buildServer(server));
        }
    }

    private Server buildServer(ApiDocs.Server server) {
        return new Server()
            .url(server.getUrl())
            .description(server.getDescription());
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return this.order;
    }
}

