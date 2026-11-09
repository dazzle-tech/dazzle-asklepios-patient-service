package com.dazzle.asklepios.service.dto.patientProcedure;

import java.io.Serializable;
import java.time.Instant;

public record PatientProcedureRegistrationDTO(
        String consentForm,
        Instant registrationDateTime,
        Long performingDepartmentId,
        Long performingPractitionerId
) implements Serializable {}
