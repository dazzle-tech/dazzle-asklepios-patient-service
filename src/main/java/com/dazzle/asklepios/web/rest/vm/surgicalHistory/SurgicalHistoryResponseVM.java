package com.dazzle.asklepios.web.rest.vm.surgicalHistory;

import com.dazzle.asklepios.domain.SurgicalHistory;

import java.time.Instant;
import java.util.Date;

public record SurgicalHistoryResponseVM(
        Long id,
        Long patientId,
        String surgery,
        Date dateOfSurgery,
        String facility,
        String anesthesiaType,
        String complications,
        String adverseReactionsToAnesthesia,
        Boolean hasImplantsOrDevices,
        String implantsOrDevicesDescription,

        // Cancel fields
        String status,
        String cancelledBy,
        Date cancelledDate,
        String cancellationReason,

        // Auditing fields
        String createdBy,
        Instant createdDate,
        String lastModifiedBy,
        Instant lastModifiedDate
) {
    public static SurgicalHistoryResponseVM ofEntity(SurgicalHistory entity) {
        return new SurgicalHistoryResponseVM(
                entity.getId(),
                entity.getPatient().getId(),
                entity.getSurgery(),
                entity.getDateOfSurgery(),
                entity.getFacility(),
                entity.getAnesthesiaType(),
                entity.getComplications(),
                entity.getAdverseReactionsToAnesthesia(),
                entity.getHasImplantsOrDevices(),
                entity.getImplantsOrDevicesDescription(),

                // Cancel fields
                entity.getStatus(),
                entity.getCancelledBy(),
                entity.getCancelledDate(),
                entity.getCancellationReason(),

                // Auditing fields
                entity.getCreatedBy(),
                entity.getCreatedDate(),
                entity.getLastModifiedBy(),
                entity.getLastModifiedDate()
        );
    }
}