package com.dazzle.asklepios.service.dto.patient;

import java.time.LocalDate;

public record FacilityPatientFilterDTO(

        String patientName,

        LocalDate registrationDateFrom,

        LocalDate registrationDateTo,

        Long insuranceId

) {
}