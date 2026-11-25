package com.dazzle.asklepios.domain.converter;

import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Stores the selected tooth surfaces in the existing {@code surface} column as a comma-separated
 * list of {@link ToothSurface} names (for example {@code MESIAL,OCCLUSAL}). Older single-surface
 * values such as {@code MESIAL} read back as a one-item list.
 */
@Converter
public class ToothSurfaceListConverter implements AttributeConverter<List<ToothSurface>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(List<ToothSurface> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }

        return attribute.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(Enum::name)
                .collect(Collectors.joining(SEPARATOR));
    }

    @Override
    public List<ToothSurface> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }

        return Arrays.stream(dbData.split(SEPARATOR))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(ToothSurface::valueOf)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
