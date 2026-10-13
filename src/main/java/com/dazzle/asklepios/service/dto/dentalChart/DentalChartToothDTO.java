package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.enumeration.ToothNumber;

import java.io.Serializable;
import java.util.List;

public record DentalChartToothDTO(
        Long id,
        Long chartId,
        ToothNumber toothNumber,
        boolean missing,
        List<ToothActionDTO> actions,
        List<ToothActionLogDTO> history,
        List<ToothServiceDTO> services,
        List<ToothCdtDTO> cdts
) implements Serializable {

    public static DentalChartToothDTO fromEntity(
            DentalChartTooth entity,
            List<ToothActionDTO> actions,
            List<ToothActionLogDTO> history,
            List<ToothServiceDTO> services,
            List<ToothCdtDTO> cdts
    ) {
        return new DentalChartToothDTO(
                entity.getId(),
                entity.getChartId(),
                entity.getToothNumber(),
                entity.isMissing(),
                actions,
                history,
                services,
                cdts
        );
    }
}
