package com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders;

import java.time.Instant;

public record AdministerMedicationOrderDTO(
        Instant actualAdministerTime
) {
}
