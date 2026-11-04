package com.dazzle.asklepios.service.dto.patientEncounter;

import java.io.Serializable;

public record DiagnosticCheckoutResultLine(
        String name,
        String value,
        String unit,
        String status
) implements Serializable {
}
