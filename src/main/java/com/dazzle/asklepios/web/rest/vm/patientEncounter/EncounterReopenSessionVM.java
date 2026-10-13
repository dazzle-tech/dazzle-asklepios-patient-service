package com.dazzle.asklepios.web.rest.vm.patientEncounter;

import com.dazzle.asklepios.domain.EncounterReopenSession;
import com.dazzle.asklepios.domain.enumeration.DischargeType;
import com.dazzle.asklepios.domain.enumeration.EncounterReopenSessionStatus;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.TypeOfReopen;

import java.time.Instant;
import java.time.LocalDateTime;

public record EncounterReopenSessionVM(
        Long id,
        Long encounterId,
        Integer sessionNumber,
        TypeOfReopen typeOfReopen,
        String reason,
        TreatmentStatus originalTreatmentStatus,
        String reopenedBy,
        Instant reopenedAt,
        String closedBy,
        Instant closedAt,
        EncounterReopenSessionStatus status,
        String completedBy,
        Instant completedAt,
        LocalDateTime dischargeAt,
        DischargeType dischargeType
) {

    public static EncounterReopenSessionVM ofEntity(EncounterReopenSession session) {
        if (session == null) {
            return null;
        }

        Long encounterId = session.getEncounter() != null ? session.getEncounter().getId() : null;

        return new EncounterReopenSessionVM(
                session.getId(),
                encounterId,
                session.getSessionNumber(),
                session.getTypeOfReopen(),
                session.getReason(),
                session.getOriginalTreatmentStatus(),
                session.getReopenedBy(),
                session.getReopenedAt(),
                session.getClosedBy(),
                session.getClosedAt(),
                session.getStatus(),
                session.getCompletedBy(),
                session.getCompletedAt(),
                session.getDischargeAt(),
                session.getDischargeType()
        );
    }
}
