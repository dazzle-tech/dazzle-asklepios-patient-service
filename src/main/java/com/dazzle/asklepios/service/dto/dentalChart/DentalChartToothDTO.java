package com.dazzle.asklepios.service.dto.dentalChart;

import com.dazzle.asklepios.domain.DentalChartTooth;

import java.io.Serializable;
import java.util.List;

public record DentalChartToothDTO(
        Long id,
        Long chartId,
        String toothNumber,
        boolean missing,
        String inventoryStatus,
        int pendingTreatments,
        String inventoryProtection,
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
            List<ToothCdtDTO> cdts,
            int pendingTreatments,
            String inventoryProtection
    ) {
        return new DentalChartToothDTO(
                entity.getId(),
                entity.getChartId(),
                entity.getToothNumber(),
                entity.isMissing(),
                entity.getInventoryStatus() != null ? entity.getInventoryStatus().name() : "ACTIVE",
                pendingTreatments,
                inventoryProtection,
                actions,
                history,
                services,
                cdts
        );
    }
}
