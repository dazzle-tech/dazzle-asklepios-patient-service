package com.dazzle.asklepios.domain.converter;

import com.dazzle.asklepios.domain.enumeration.EncounterLifecycleStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncounterLifecycleStatusConverter
        implements AttributeConverter<EncounterLifecycleStatus, String> {

    @Override
    public String convertToDatabaseColumn(EncounterLifecycleStatus attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public EncounterLifecycleStatus convertToEntityAttribute(String dbData) {
        return EncounterLifecycleStatus.fromDatabaseValue(dbData);
    }
}
