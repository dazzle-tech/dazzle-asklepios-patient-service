package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.DentalToothApprovalStatus;

/** The insurance state of one tooth of a dental procedure (one billing line per tooth). */
public record DentalToothApproval(String toothNumber, DentalToothApprovalStatus status) {}
