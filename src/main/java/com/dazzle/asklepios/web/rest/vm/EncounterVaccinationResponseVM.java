package com.dazzle.asklepios.web.rest.vm;

import com.dazzle.asklepios.domain.EncounterVaccination;
import com.dazzle.asklepios.domain.enumeration.EncounterVaccinationStatus;

import java.io.Serializable;
import java.time.Instant;

public record EncounterVaccinationResponseVM(

        Long id,
        Long patientId,
        Long encounterId,
        Long vaccineId,
        Long vaccineBrandId,
        Long vaccineDoseId,
        String vaccineLotNumber,

        Instant dateAdministered,

        EncounterVaccinationStatus status,
        String cancellationReason,

        Instant cancelledAt,
        Long cancelledById,

        String administeredLocation,
        String administrationReactions,
        String externalFacilityName,
        String notes,

        Instant reviewedAt,
        Long reviewedById,

        Instant createdDate,
        String createdBy,

        Instant lastModifiedDate,
        String lastModifiedBy

) implements Serializable {

    public static EncounterVaccinationResponseVM ofEntity(EncounterVaccination entity) {
        return new EncounterVaccinationResponseVM(
                entity.getId(),
                entity.getPatient() != null ? entity.getPatient().getId() : null,
                entity.getEncounterId(),
                entity.getVaccineId(),
                entity.getVaccineBrandId(),
                entity.getVaccineDoseId(),
                entity.getVaccineLotNumber(),
                entity.getDateAdministered(),
                entity.getStatus(),
                entity.getCancellationReason(),
                entity.getCancelledAt(),
                entity.getCancelledById(),
                entity.getAdministeredLocation(),
                entity.getAdministrationReactions(),
                entity.getExternalFacilityName(),
                entity.getNotes(),
                entity.getReviewedAt(),
                entity.getReviewedById(),
                entity.getCreatedDate(),
                entity.getCreatedBy(),
                entity.getLastModifiedDate(),
                entity.getLastModifiedBy()
        );
    }
}
