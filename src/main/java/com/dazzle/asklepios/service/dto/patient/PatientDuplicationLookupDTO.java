package com.dazzle.asklepios.service.dto.patient;
import java.time.LocalDate;

public record PatientDuplicationLookupDTO(
        Long ruleId,
        LocalDate dateOfBirth,
        String gender,
        String firstName,
        String lastName,
        String documentNo,
        String mobileNumber
) {}