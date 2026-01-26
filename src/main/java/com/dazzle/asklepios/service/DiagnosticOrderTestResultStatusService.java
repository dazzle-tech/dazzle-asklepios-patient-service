// src/main/java/com/dazzle/asklepios/service/DiagnosticOrderTestResultStatusService.java
package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class DiagnosticOrderTestResultStatusService {

    private final DiagnosticOrderTestResultRepository resultRepository;
    private final DiagnosticOrderTestStatusService diagnosticOrderTestStatusService;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;
    public DiagnosticOrderTestResultStatusService(
            DiagnosticOrderTestResultRepository resultRepository,
            DiagnosticOrderTestStatusService diagnosticOrderTestStatusService, DiagnosticOrderTestRepository diagnosticOrderTestRepository, DiagnosticOrderStatusService diagnosticOrderStatusService
    ) {
        this.resultRepository = resultRepository;
        this.diagnosticOrderTestStatusService = diagnosticOrderTestStatusService;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
    }

    public DiagnosticOrderTestResult approve(Long resultId, String approvedBy) {
        DiagnosticOrderTestResult r = getResult(resultId);

        DiagnosticStatus from = normalize(r.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.RESULT_APPROVED);

        r.setProcessingStatus(DiagnosticStatus.RESULT_APPROVED);
        r.setApprovedBy(approvedBy);
        r.setApprovedDate(Instant.now());

        DiagnosticOrderTestResult saved = resultRepository.save(r);

        // update test status too (will recompute lab/rad)
        diagnosticOrderTestStatusService.approve(saved.getOrderTestId());
        this.recomputeTestProcessingStatusFromResults(saved.getOrderTestId());

        return saved;
    }

    public DiagnosticOrderTestResult reject(Long resultId, String rejectedBy, String rejectedReason) {
        DiagnosticOrderTestResult r = getResult(resultId);

        DiagnosticStatus from = normalize(r.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.REJECTED);

        r.setProcessingStatus(DiagnosticStatus.REJECTED);
        r.setRejectedBy(rejectedBy);
        r.setRejectedReason(rejectedReason);
        r.setRejectedDate(Instant.now());

        DiagnosticOrderTestResult saved = resultRepository.save(r);

        // update test status too (will recompute lab/rad)
        diagnosticOrderTestStatusService.reject(saved.getOrderTestId(), rejectedBy, rejectedReason);
        this.recomputeTestProcessingStatusFromResults(saved.getOrderTestId());
        return saved;
    }

    private DiagnosticOrderTestResult getResult(Long id) {
        return resultRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests_result",
                        "DiagnosticOrderTestResult not found with id " + id
                ));
    }

    private DiagnosticStatus normalize(DiagnosticStatus status) {
        return status == null ? DiagnosticStatus.NEW : status;
    }

    private void ensureTransition(DiagnosticStatus from, DiagnosticStatus to) {
        // RESULT_READY -> RESULT_APPROVED
        if (to == DiagnosticStatus.RESULT_APPROVED) {
            if (!(from == DiagnosticStatus.RESULT_READY )) throw invalid(from, to);
            return;
        }

        // allow REJECTED from NEW / RESULT_READY
        if (to == DiagnosticStatus.REJECTED) {
            if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.RESULT_READY || from == DiagnosticStatus.REVIEWED))
                throw invalid(from, to);
            return;
        }

        throw invalid(from, to);
    }

    private BadRequestAlertException invalid(DiagnosticStatus from, DiagnosticStatus to) {
        return new BadRequestAlertException(
                "invalid_transition",
                "diagnostic_order_tests_result",
                "Invalid transition " + from + " -> " + to
        );
    }
    public DiagnosticOrderTest recomputeTestProcessingStatusFromResults(Long orderTestId) {

        DiagnosticOrderTest test = diagnosticOrderTestRepository.findById(orderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + orderTestId
                ));

        if (!resultRepository.existsByOrderTestId(orderTestId)) {
            return test;
        }

        List<DiagnosticStatus> statuses = resultRepository.findProcessingStatusesByOrderTestId(orderTestId);
        DiagnosticStatus target = aggregate(statuses);

        test.setProcessingStatus(target);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);

        // keep order in sync with updated test
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }
    public DiagnosticOrderTestResult toggleReview(Long resultId, String username) {
        DiagnosticOrderTestResult r = getResult(resultId);

        boolean reviewed = r.getReviewDate() != null;

        if (!reviewed) {
            r.setReviewBy(username);
            r.setReviewDate(Instant.now());
        } else {
            r.setReviewBy(null);
            r.setReviewDate(null);
        }

        return resultRepository.save(r);
    }

    private DiagnosticStatus aggregate(List<DiagnosticStatus> statuses) {

        if (statuses.stream().anyMatch(s -> s == DiagnosticStatus.REJECTED)) {
            return DiagnosticStatus.REJECTED;
        }

        if (statuses.stream().allMatch(s -> s == DiagnosticStatus.RESULT_APPROVED)) {
            return DiagnosticStatus.RESULT_APPROVED;
        }



        if (statuses.stream().anyMatch(s -> s == DiagnosticStatus.RESULT_READY)) {
            return DiagnosticStatus.RESULT_READY;
        }

        return DiagnosticStatus.NEW;
    }
}
