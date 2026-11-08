package com.dazzle.asklepios.integration.waseel.event;

/**
 * Asks for the billing responsibilities of an encounter to be recalculated once the
 * pre-authorization decision commits. Recalculating inside the decision transaction
 * would wait on the billing lines that transaction has already changed.
 */
public record PreAuthorizationDecisionCommittedEvent(Long encounterId) {}
