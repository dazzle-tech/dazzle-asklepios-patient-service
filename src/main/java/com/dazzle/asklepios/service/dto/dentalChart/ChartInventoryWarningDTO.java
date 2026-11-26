package com.dazzle.asklepios.service.dto.dentalChart;

import java.io.Serializable;

/** A removed or replaced tooth that still has open (pending or in-progress) planned treatment. */
public record ChartInventoryWarningDTO(
        String toothNumber,
        int pendingTreatments
) implements Serializable {}
