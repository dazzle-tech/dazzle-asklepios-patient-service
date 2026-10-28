package com.dazzle.asklepios.integration.waseel.dto;

import java.math.BigDecimal;

/**
 * Contract split of a local pre-approval request line, shown before recording a partial approval.
 */
public record LocalPreAuthorizationCoverageResponse(
        BigDecimal netAmount,
        BigDecimal patientShare,
        BigDecimal insuranceShare
) {}
