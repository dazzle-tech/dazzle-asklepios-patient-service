package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.EncounterReopenSession;
import com.dazzle.asklepios.domain.enumeration.EncounterReopenSessionStatus;
import com.dazzle.asklepios.repository.EncounterReopenSessionRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Single lookup for an OPEN encounter reopen session.
 * Mutation services call this instead of querying the session repository themselves.
 */
@Service
public class EncounterReopenGuard {

    public static final String ERROR_KEY = "reopen.moduleReadOnly";
    public static final String MESSAGE =
            "Modification is not allowed for this module while the encounter is reopened.";
    public static final String ENTITY_NAME = "encounterReopenSession";

    private final EncounterReopenSessionRepository encounterReopenSessionRepository;

    public EncounterReopenGuard(EncounterReopenSessionRepository encounterReopenSessionRepository) {
        this.encounterReopenSessionRepository = encounterReopenSessionRepository;
    }

    public boolean hasOpenReopenSession(Long encounterId) {
        return isAmendmentOpen(encounterId);
    }

    public boolean isAmendmentOpen(Long encounterId) {
        return findOpenReopenSessionId(encounterId).isPresent();
    }

    public Optional<Long> findOpenReopenSessionId(Long encounterId) {
        if (encounterId == null) {
            return Optional.empty();
        }
        return encounterReopenSessionRepository
                .findByEncounter_IdAndStatus(encounterId, EncounterReopenSessionStatus.OPEN)
                .map(EncounterReopenSession::getId);
    }

    public void rejectIfOpenReopenSession(Long encounterId) {
        if (hasOpenReopenSession(encounterId)) {
            throw new BadRequestAlertException(MESSAGE, ENTITY_NAME, ERROR_KEY);
        }
    }
}
