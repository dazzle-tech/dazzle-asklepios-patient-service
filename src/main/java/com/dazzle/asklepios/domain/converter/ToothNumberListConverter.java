package com.dazzle.asklepios.domain.converter;

import com.dazzle.asklepios.domain.enumeration.ToothNumber;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stores selected teeth in the existing {@code tooth_number} column as a
 * comma-separated list (for example {@code Tooth1,Tooth5}). A legacy single
 * value such as {@code Tooth1} still reads back as a one-item list.
 */
@Converter
public class ToothNumberListConverter implements AttributeConverter<List<ToothNumber>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(List<ToothNumber> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }

        return attribute.stream()
                .filter(tooth -> tooth != null)
                .map(Enum::name)
                .distinct()
                .collect(Collectors.joining(SEPARATOR));
    }

    @Override
    public List<ToothNumber> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }

        return Arrays.stream(dbData.split(SEPARATOR))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(ToothNumber::valueOf)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
