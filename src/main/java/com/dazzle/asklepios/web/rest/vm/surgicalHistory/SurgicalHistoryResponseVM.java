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
        String createdBy,
        Instant createdDate
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
                entity.getCreatedBy(),
                entity.getCreatedDate()
        );
    }
}
