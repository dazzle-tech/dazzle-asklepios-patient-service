package com.dazzle.asklepios.web.rest.vm.laboratory;

import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record DiagnosticOrderTestResultResultsVM(
        Long id,
        Long orderTestId,
        Long testId,
        Long profileTestId,

        BigDecimal resultValueNumber,
        String resultValueText,

        TestResultMarker marker,
        TestResultMarker viewMarker,
        String viewNormalRange,

        Instant resultDate,

        String patientName,
        String mrn,

        String orderedBy,
        Instant orderedAt,

        Long encounterId,

        boolean hasNote,
        boolean isRadiology
) implements Serializable {
}