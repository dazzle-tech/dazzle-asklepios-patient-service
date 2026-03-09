package com.dazzle.asklepios.service.dto.patient;

import java.util.Date;

public record PatientLabelDTO(
        Long patientId,
        String patientFullName,
        String mrn,
        Date dateOfBirth,
        Integer age,
        String gender,
        Date registrationDate
) {}