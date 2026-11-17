package com.dazzle.asklepios.service.dto.dentalChart;

import java.io.Serializable;
import java.util.List;

public record ChartInventoryResultDTO(
        DentalChartResponseDTO chart,
        List<ChartInventoryWarningDTO> warnings
) implements Serializable {}
