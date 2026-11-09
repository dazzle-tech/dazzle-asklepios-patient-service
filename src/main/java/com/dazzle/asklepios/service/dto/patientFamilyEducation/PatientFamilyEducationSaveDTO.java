package com.dazzle.asklepios.service.dto.patientFamilyEducation;

import com.dazzle.asklepios.domain.enumeration.PatientFamilyEducationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record PatientFamilyEducationSaveDTO(
        Long id,
        @NotNull Long patientId,
        @NotNull Long encounterId,
        @NotBlank String topic,
        PatientFamilyEducationMethod method,
        String effectiveness
) implements Serializable {}
