package com.dazzle.asklepios.web.rest.vm.laboratory;

import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.diagnostictest.TestResultMarker;

import java.math.BigDecimal;
import java.time.Instant;

public record PatientDiagnosticResultHistoryVM(
        Long orderId,
        Long orderTestId,
        Long resultId,
        Long profileTestId,
        Instant resultDate,
        BigDecimal resultValueNumber,
        Instant reviewDate,
        String resultValueText,
        TestResultMarker marker,
        String normalRangeValue,
        DiagnosticStatus processingStatus
) {
    public static PatientDiagnosticResultHistoryVM of(Long orderId, Instant resultDate, DiagnosticOrderTestResult diagnosticOrderTestResult) {
        return new PatientDiagnosticResultHistoryVM(
                orderId,
                diagnosticOrderTestResult.getOrderTestId(),
                diagnosticOrderTestResult.getId(),
                diagnosticOrderTestResult.getProfileTestId(),
                resultDate,
                diagnosticOrderTestResult.getResultValueNumber(),
                diagnosticOrderTestResult.getReviewDate(),
                diagnosticOrderTestResult.getResultValueText(),
                diagnosticOrderTestResult.getMarker(),
                diagnosticOrderTestResult.getNormalRangeValue(),
                diagnosticOrderTestResult.getProcessingStatus()
        );
    }
}
