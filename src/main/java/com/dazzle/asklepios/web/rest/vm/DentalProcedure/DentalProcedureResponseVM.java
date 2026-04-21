package com.dazzle.asklepios.web.rest.vm.DentalProcedure;

import com.dazzle.asklepios.domain.DentalProcedure;
import com.dazzle.asklepios.domain.enumeration.ToothNumber;

import java.math.BigDecimal;
import java.time.Instant;

public record DentalProcedureResponseVM(
        Long id,
        Long patientId,
        Long encounterId,
        ToothNumber toothNumber,
        String surface,
        String anesthesiaUsed,
        BigDecimal dose,
        String unit,
        String fillingMaterial,
        Long serviceId,
        Long cdtCodeId,
        String notes,
        boolean cancelled,
        String createdBy,
        Instant createdDate,
        String lastModifiedBy,
        Instant lastModifiedDate
) {
    public static DentalProcedureResponseVM ofEntity(DentalProcedure entity) {
        return new DentalProcedureResponseVM(
                entity.getId(),
                entity.getPatientId(),
                entity.getEncounterId(),
                entity.getToothNumber(),
                entity.getSurface(),
                entity.getAnesthesiaUsed(),
                entity.getDose(),
                entity.getUnit(),
                entity.getFillingMaterial(),
                entity.getServiceId(),
                entity.getCdtCodeId(),
                entity.getNotes(),
                entity.isCancelled(),
                entity.getCreatedBy(),
                entity.getCreatedDate(),
                entity.getLastModifiedBy(),
                entity.getLastModifiedDate()
        );
    }
}