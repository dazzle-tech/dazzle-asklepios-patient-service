package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;

public class WaseelFactorJsonSerializer extends JsonSerializer<BigDecimal> {

    @Override
    public void serialize(
            BigDecimal value,
            JsonGenerator generator,
            SerializerProvider serializers
    ) throws IOException {
        BigDecimal formatted = WaseelFactorFormatter.format(value);
        generator.writeNumber(formatted.toPlainString());
    }
}
