package com.dazzle.asklepios.service;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/**
 * Binds the current OPEN reopen session to the PostgreSQL transaction that
 * writes a clinical row. Audit triggers read {@code app.reopen_session_id}.
 * <p>
 * {@code set_config(..., true)} is SET LOCAL: the value lasts only until this
 * transaction commits or rolls back, so a pooled connection cannot carry it
 * into the next request.
 */
@Component
public class ReopenSessionAuditContext {

    static final String SETTING = "app.reopen_session_id";

    private final EntityManager entityManager;
    private final EncounterReopenGuard encounterReopenGuard;

    public ReopenSessionAuditContext(
            EntityManager entityManager,
            EncounterReopenGuard encounterReopenGuard
    ) {
        this.entityManager = entityManager;
        this.encounterReopenGuard = encounterReopenGuard;
    }

    public void applyOpenSession(Long encounterId) {
        bind(encounterReopenGuard.findOpenReopenSessionId(encounterId).orElse(null));
    }

    public void bind(Long reopenSessionId) {
        String value = reopenSessionId == null ? "" : Long.toString(reopenSessionId);
        entityManager.createNativeQuery(
                        "select set_config('" + SETTING + "', :reopenSessionId, true)"
                )
                .setParameter("reopenSessionId", value)
                .getSingleResult();
    }
}
