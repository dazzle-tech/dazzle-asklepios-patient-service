package com.dazzle.asklepios.service.dto.patient;
import java.util.Date;

public record PatientDuplicationLookupDTO(
        Long ruleId,
        Date dateOfBirth,
        String gender,
        String firstName,
        String lastName,
        String documentNo,
        String mobileNumber
) {}