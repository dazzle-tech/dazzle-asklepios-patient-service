package com.dazzle.asklepios.service.dto.patient;

import jakarta.validation.constraints.NotNull;

public record UnknownPatientCreateDTO(
        @NotNull Boolean isUnknown,
        @NotNull Boolean isVerified,
        @NotNull Boolean isCompletedPatient
) {
    public static UnknownPatientCreateDTO defaultUnknown() {
        return new UnknownPatientCreateDTO(true, false, false);
    }
}
