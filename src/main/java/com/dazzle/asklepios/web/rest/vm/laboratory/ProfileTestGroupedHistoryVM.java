package com.dazzle.asklepios.web.rest.vm.laboratory;

import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;

import java.time.Instant;
import java.util.List;

public record ProfileTestGroupedHistoryVM(
        Long profileTestId,
        Instant latestResultDate,
        DiagnosticStatus latestProcessingStatus,
        List<PatientDiagnosticResultHistoryVM> results
) {}
