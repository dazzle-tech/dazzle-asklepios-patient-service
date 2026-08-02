package com.dazzle.asklepios.service.dto.radiology;

import java.time.LocalDate;

public record PacsStudyDTO(
        String patientName,
        String patientId,
        String studyId,
        LocalDate studyDate,
        String link
) {
}