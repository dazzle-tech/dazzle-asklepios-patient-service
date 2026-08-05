package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.service.dto.workingDays.WorkingDayJson;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PractitionerDTO(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String specialty,
        String subSpecialty,
        String defaultMedicalLicense,
        String secondaryMedicalLicense,
        String educationalLevel,
        String jobRole,
        List<WorkingDayJson> workingDays,
        Long userId
) {}
