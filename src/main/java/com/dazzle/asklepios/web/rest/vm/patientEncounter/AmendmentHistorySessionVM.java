package com.dazzle.asklepios.web.rest.vm.patientEncounter;

import com.dazzle.asklepios.domain.EncounterReopenSession;
import com.dazzle.asklepios.domain.enumeration.EncounterReopenSessionStatus;
import com.dazzle.asklepios.domain.enumeration.TypeOfReopen;

import java.time.Instant;
import java.util.List;

public record AmendmentHistorySessionVM(
        Long sessionId,
        Integer sessionNumber,
        TypeOfReopen typeOfReopen,
        String reason,
        EncounterReopenSessionStatus status,
        String amendedBy,
        Instant amendedAt,
        String closedBy,
        Instant closedAt,
        List<AmendmentHistoryChangeVM> changes
) {

    public static AmendmentHistorySessionVM of(
            EncounterReopenSession session,
            List<AmendmentHistoryChangeVM> changes
    ) {
        return new AmendmentHistorySessionVM(
                session.getId(),
                session.getSessionNumber(),
                session.getTypeOfReopen(),
                session.getReason(),
                session.getStatus(),
                session.getReopenedBy(),
                session.getReopenedAt(),
                session.getClosedBy(),
                session.getClosedAt(),
                changes
        );
    }
}
