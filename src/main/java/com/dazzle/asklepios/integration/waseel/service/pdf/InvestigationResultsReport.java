package com.dazzle.asklepios.integration.waseel.service.pdf;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record InvestigationResultsReport(
        String patientName,
        String medicalRecordNumber,
        LocalDate dateOfBirth,
        String gender,
        String encounterNumber,
        LocalDate encounterDate,
        List<OrderSection> orders
) {

    public record OrderSection(
            String orderNumber,
            String encounterNumber,
            String fromDepartment,
            List<OrderTestSection> orderTests
    ) {}

    public record OrderTestSection(
            String testName,
            String receivedDepartment,
            List<LabResultRow> labResults,
            List<RadiologyResult> radiologyResults
    ) {
        public boolean hasResults() {
            return (labResults != null && !labResults.isEmpty())
                    || (radiologyResults != null && !radiologyResults.isEmpty());
        }
    }

    public record LabResultRow(
            String testName,
            String category,
            String result,
            String unit,
            String normalRange,
            String flag,
            boolean abnormal,
            Instant resultDate
    ) {}

    public record RadiologyResult(
            String status,
            String severity,
            Instant reportDate,
            String report,
            String criticalFindings,
            String radiologistComments,
            String radiologistInformation,
            String reviewedBy,
            String approvedBy
    ) {}

    public boolean hasResults() {
        return orders != null && orders.stream()
                .anyMatch(order -> order.orderTests() != null
                        && order.orderTests().stream().anyMatch(OrderTestSection::hasResults));
    }
}
