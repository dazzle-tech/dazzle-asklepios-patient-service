package com.dazzle.asklepios.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiEnumCustomizer {

    @Bean
    public OpenApiCustomizer addEnumerationsObject(com.dazzle.asklepios.service.EnumRegistry enumRegistry) {
        return new OpenApiCustomizer() {
            @Override
            public void customise(OpenAPI openAPI) {
                Map<String, List<String>> enums = enumRegistry.getAll();

                if (openAPI.getComponents() == null) {
                    openAPI.setComponents(new Components());
                }

                ObjectSchema enumerations = new ObjectSchema();
                enumerations.setDescription("All application enums as arrays of strings");

                enums.forEach((enumName, values) -> {
                    StringSchema itemSchema = new StringSchema();
                    itemSchema.setEnum(values);

                    ArraySchema arraySchema = new ArraySchema();
                    arraySchema.setItems(itemSchema);

                    enumerations.addProperties(enumName, arraySchema);
                });

                openAPI.getComponents().addSchemas("Enumerations", enumerations);
            }
        };
    }
}
