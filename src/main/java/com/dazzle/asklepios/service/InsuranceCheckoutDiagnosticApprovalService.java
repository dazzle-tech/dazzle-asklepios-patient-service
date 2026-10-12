package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.DiagnosticTest;
import com.dazzle.asklepios.domain.DiagnosticTestProfile;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.DiagnosticTestProfileRepository;
import com.dazzle.asklepios.repository.DiagnosticTestRepository;
import com.dazzle.asklepios.service.dto.patientEncounter.DiagnosticCheckoutApproval;
import com.dazzle.asklepios.service.dto.patientEncounter.DiagnosticCheckoutResultLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsuranceCheckoutDiagnosticApprovalService {

    private static final EnumSet<DiagnosticStatus> CLOSED_STATUSES =
            EnumSet.of(
                    DiagnosticStatus.CANCELLED,
                    DiagnosticStatus.REJECTED
            );

    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderTestResultRepository diagnosticOrderTestResultRepository;
    private final DiagnosticOrderTestReportRepository diagnosticOrderTestReportRepository;
    private final DiagnosticTestRepository diagnosticTestRepository;
    private final DiagnosticTestProfileRepository diagnosticTestProfileRepository;

    public List<DiagnosticCheckoutApproval> findUnapproved(Long encounterId) {
        List<DiagnosticOrder> orders =
                diagnosticOrderRepository.findByEncounterIdAndStatusNot(
                        encounterId,
                        DiagnosticStatus.CANCELLED
                );

        if (orders.isEmpty()) {
            return List.of();
        }

        Map<Long, DiagnosticOrder> ordersById = orders.stream()
                .collect(Collectors.toMap(
                        DiagnosticOrder::getId,
                        Function.identity(),
                        (left, right) -> left
                ));

        List<DiagnosticOrderTest> tests = diagnosticOrderTestRepository
                .findByOrderIdInAndStatusNot(ordersById.keySet(), DiagnosticOrderTestStatus.CANCELLED)
                .stream()
                .filter(test -> !CLOSED_STATUSES.contains(test.getProcessingStatus()))
                .toList();

        if (tests.isEmpty()) {
            return List.of();
        }

        Map<Long, String> testNames = loadTestNames(tests);

        List<Long> labTestIds = tests.stream()
                .filter(test -> test.getOrderType() == TestType.LABORATORY)
                .map(DiagnosticOrderTest::getId)
                .toList();
        List<Long> radiologyTestIds = tests.stream()
                .filter(test -> test.getOrderType() == TestType.RADIOLOGY)
                .map(DiagnosticOrderTest::getId)
                .toList();

        Map<Long, List<DiagnosticOrderTestResult>> resultsByTest = labTestIds.isEmpty()
                ? Map.of()
                : diagnosticOrderTestResultRepository.findByOrderTestIdIn(labTestIds).stream()
                .collect(Collectors.groupingBy(DiagnosticOrderTestResult::getOrderTestId));

        Map<Long, DiagnosticOrderTestReport> reportByTest = radiologyTestIds.isEmpty()
                ? Map.of()
                : diagnosticOrderTestReportRepository.findByOrderTestIdIn(radiologyTestIds).stream()
                .collect(Collectors.toMap(
                        DiagnosticOrderTestReport::getOrderTestId,
                        Function.identity(),
                        (left, right) -> left
                ));

        Map<Long, DiagnosticTestProfile> profilesById = loadProfiles(resultsByTest);

        List<DiagnosticCheckoutApproval> unapproved = new ArrayList<>();
        for (DiagnosticOrderTest test : tests) {
            if (test.getOrderType() == TestType.RADIOLOGY) {
                addRadiologyIfUnapproved(unapproved, test, ordersById, testNames, reportByTest);
            } else if (test.getOrderType() == TestType.LABORATORY) {
                addLaboratoryIfUnapproved(
                        unapproved,
                        test,
                        ordersById,
                        testNames,
                        resultsByTest,
                        profilesById
                );
            }
        }

        return unapproved;
    }

    private void addLaboratoryIfUnapproved(
            List<DiagnosticCheckoutApproval> unapproved,
            DiagnosticOrderTest test,
            Map<Long, DiagnosticOrder> ordersById,
            Map<Long, String> testNames,
            Map<Long, List<DiagnosticOrderTestResult>> resultsByTest,
            Map<Long, DiagnosticTestProfile> profilesById
    ) {
        List<DiagnosticOrderTestResult> results =
                resultsByTest.getOrDefault(test.getId(), List.of());

        boolean approved = !results.isEmpty()
                && results.stream().allMatch(result ->
                result.getProcessingStatus() == DiagnosticStatus.RESULT_APPROVED
        );

        if (approved) {
            return;
        }

        List<DiagnosticCheckoutResultLine> lines = results.stream()
                .filter(result -> result.getProcessingStatus() != DiagnosticStatus.RESULT_APPROVED)
                .map(result -> toResultLine(result, profilesById))
                .toList();

        unapproved.add(approval(
                test,
                ordersById,
                testNames,
                TestType.LABORATORY.name(),
                enumName(resolveLaboratoryStatus(test, results)),
                null,
                null,
                results.isEmpty()
                        ? "No result has been approved"
                        : "Result is not approved",
                lines
        ));
    }

    private void addRadiologyIfUnapproved(
            List<DiagnosticCheckoutApproval> unapproved,
            DiagnosticOrderTest test,
            Map<Long, DiagnosticOrder> ordersById,
            Map<Long, String> testNames,
            Map<Long, DiagnosticOrderTestReport> reportByTest
    ) {
        DiagnosticOrderTestReport report = reportByTest.get(test.getId());
        boolean approved = test.getProcessingStatus() == DiagnosticStatus.RESULT_APPROVED
                || (report != null
                && report.getProcessingStatus() == DiagnosticStatus.RESULT_APPROVED);

        if (approved) {
            return;
        }

        DiagnosticStatus approvalStatus = report == null
                ? null
                : report.getProcessingStatus();

        unapproved.add(approval(
                test,
                ordersById,
                testNames,
                TestType.RADIOLOGY.name(),
                approvalStatus == null ? null : approvalStatus.name(),
                report == null || report.getImageStatus() == null
                        ? null
                        : report.getImageStatus().name(),
                report == null ? null : blankToNull(report.getAccessionNumber()),
                report == null
                        ? "No report has been approved"
                        : "Report is not approved",
                List.of()
        ));
    }

    private DiagnosticCheckoutApproval approval(
            DiagnosticOrderTest test,
            Map<Long, DiagnosticOrder> ordersById,
            Map<Long, String> testNames,
            String orderType,
            String approvalStatus,
            String imageStatus,
            String accessionNumber,
            String message,
            List<DiagnosticCheckoutResultLine> results
    ) {
        DiagnosticOrder order = ordersById.get(test.getOrderId());
        return new DiagnosticCheckoutApproval(
                test.getId(),
                test.getTestId(),
                test.getTestId() == null ? null : blankToNull(testNames.get(test.getTestId())),
                orderType,
                order == null ? null : blankToNull(order.getOrderNumber()),
                order != null && Boolean.TRUE.equals(order.getIsUrgent()),
                test.getStatus() == null ? null : test.getStatus().name(),
                test.getProcessingStatus() == null
                        ? DiagnosticStatus.NEW.name()
                        : test.getProcessingStatus().name(),
                approvalStatus,
                imageStatus,
                accessionNumber,
                message,
                results
        );
    }

    private DiagnosticCheckoutResultLine toResultLine(
            DiagnosticOrderTestResult result,
            Map<Long, DiagnosticTestProfile> profilesById
    ) {
        DiagnosticTestProfile profile = result.getProfileTestId() == null
                ? null
                : profilesById.get(result.getProfileTestId());
        String name = profile == null ? null : blankToNull(profile.getName());
        String unit = profile == null ? null : blankToNull(profile.getResultUnit());
        return new DiagnosticCheckoutResultLine(
                name == null ? "Result" : name,
                formatResultValue(result),
                unit,
                result.getProcessingStatus() == null
                        ? DiagnosticStatus.NEW.name()
                        : result.getProcessingStatus().name()
        );
    }

    private Map<Long, String> loadTestNames(List<DiagnosticOrderTest> tests) {
        Set<Long> testIds = tests.stream()
                .map(DiagnosticOrderTest::getTestId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (testIds.isEmpty()) {
            return Map.of();
        }

        return diagnosticTestRepository.findAllById(testIds).stream()
                .filter(test -> test.getId() != null)
                .collect(Collectors.toMap(
                        DiagnosticTest::getId,
                        test -> test.getName() == null ? "" : test.getName(),
                        (left, right) -> left
                ));
    }

    private Map<Long, DiagnosticTestProfile> loadProfiles(
            Map<Long, List<DiagnosticOrderTestResult>> resultsByTest
    ) {
        Set<Long> profileIds = resultsByTest.values().stream()
                .flatMap(List::stream)
                .map(DiagnosticOrderTestResult::getProfileTestId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        if (profileIds.isEmpty()) {
            return Map.of();
        }

        return diagnosticTestProfileRepository.findAllById(profileIds).stream()
                .collect(Collectors.toMap(
                        DiagnosticTestProfile::getId,
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private DiagnosticStatus resolveLaboratoryStatus(
            DiagnosticOrderTest test,
            List<DiagnosticOrderTestResult> results
    ) {
        if (results.isEmpty()) {
            return test.getProcessingStatus() == null
                    ? DiagnosticStatus.NEW
                    : test.getProcessingStatus();
        }

        List<DiagnosticStatus> pending = results.stream()
                .map(DiagnosticOrderTestResult::getProcessingStatus)
                .filter(status -> status != DiagnosticStatus.RESULT_APPROVED)
                .toList();

        if (pending.isEmpty()) {
            return DiagnosticStatus.RESULT_APPROVED;
        }

        DiagnosticStatus first = pending.get(0);
        boolean sameStatus = pending.stream().allMatch(status -> status == first);
        return sameStatus ? first : DiagnosticStatus.PARTIALLY;
    }

    private String formatResultValue(DiagnosticOrderTestResult result) {
        String text = blankToNull(result.getResultValueText());
        if (text != null) {
            return text;
        }
        BigDecimal number = result.getResultValueNumber();
        if (number == null) {
            return null;
        }
        return number.stripTrailingZeros().toPlainString();
    }

    private String enumName(DiagnosticStatus status) {
        return status == null ? null : status.name();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
