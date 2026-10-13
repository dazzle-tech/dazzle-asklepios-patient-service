package com.dazzle.asklepios.service.dto.dentalChart;

import java.io.Serializable;
import java.util.List;

public record DentalTreatmentPlanResponseDTO(
        List<PlannedTreatmentDTO> draftTreatments,
        List<PlannedTreatmentDTO> currentTreatments
) implements Serializable {}
