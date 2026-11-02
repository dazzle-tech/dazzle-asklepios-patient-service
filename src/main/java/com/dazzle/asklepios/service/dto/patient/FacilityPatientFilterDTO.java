package com.dazzle.asklepios.service.dto.patient;

import java.time.LocalDate;

public record FacilityPatientFilterDTO(

        String patientName,
        String mrn,
        String primaryMobileNumber,
        String documentNumber,
        LocalDate dateOfBirth,

        LocalDate registrationDateFrom,

        LocalDate registrationDateTo,

        Long insuranceId

) {
}
