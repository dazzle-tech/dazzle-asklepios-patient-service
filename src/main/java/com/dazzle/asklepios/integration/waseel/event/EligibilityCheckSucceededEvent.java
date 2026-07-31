package com.dazzle.asklepios.integration.waseel.event;

/**
 * Published after a Waseel eligibility check succeeds for an encounter.
 * Triggers backend pre-authorization submission without a circular bean dependency.
 */
public record EligibilityCheckSucceededEvent(Long encounterId) {}
