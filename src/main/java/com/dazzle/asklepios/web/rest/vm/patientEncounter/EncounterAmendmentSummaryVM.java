package com.dazzle.asklepios.web.rest.vm.patientEncounter;

import com.dazzle.asklepios.domain.EncounterReopenSession;
import com.dazzle.asklepios.domain.enumeration.EncounterReopenSessionStatus;
import com.dazzle.asklepios.domain.enumeration.TypeOfReopen;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record EncounterAmendmentSummaryVM(
        Long encounterId,
        int amendmentCount,
        boolean amendmentOpen,
        Integer latestSessionNumber,
        TypeOfReopen latestTypeOfReopen,
        String latestReason,
        String amendedBy,
        Instant amendedAt,
        Long openSessionId
) {

    public static EncounterAmendmentSummaryVM of(Long encounterId, List<EncounterReopenSession> sessions) {
        List<EncounterReopenSession> rows = sessions == null ? List.of() : sessions;
        EncounterReopenSession latest = rows.stream()
                .max(Comparator.comparing(EncounterReopenSession::getSessionNumber, Comparator.nullsFirst(Integer::compareTo)))
                .orElse(null);
        EncounterReopenSession openSession = rows.stream()
                .filter(session -> session.getStatus() == EncounterReopenSessionStatus.OPEN)
                .findFirst()
                .orElse(null);
        return new EncounterAmendmentSummaryVM(
                encounterId,
                rows.size(),
                openSession != null,
                latest == null ? null : latest.getSessionNumber(),
                latest == null ? null : latest.getTypeOfReopen(),
                latest == null ? null : latest.getReason(),
                latest == null ? null : latest.getReopenedBy(),
                latest == null ? null : latest.getReopenedAt(),
                openSession == null ? null : openSession.getId()
        );
    }
}
