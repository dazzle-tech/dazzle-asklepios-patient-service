package com.dazzle.asklepios.service.dto.dentalChart;

public record DentalTreatmentSessionDTO(
        Long id,
        Long treatmentPlanId,
        Integer sessionNumber
) {}
