package com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult;

import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;

public record ApproveResultDTO(
        Long resultId,
        String approvedBy,
        TestResultMarker marker,
        String normalRange
) {}
