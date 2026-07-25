package com.dazzle.asklepios.domain.converter;

import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TreatmentStatusConverter
        implements AttributeConverter<TreatmentStatus, String> {

    @Override
    public String convertToDatabaseColumn(TreatmentStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public TreatmentStatus convertToEntityAttribute(String dbData) {
        return TreatmentStatus.fromDatabaseValue(dbData);
    }
}
