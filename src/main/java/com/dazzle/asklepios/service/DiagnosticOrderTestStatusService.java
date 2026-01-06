// src/main/java/com/dazzle/asklepios/service/DiagnosticOrderTestStatusService.java
package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class DiagnosticOrderTestStatusService {

    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;

    public DiagnosticOrderTestStatusService(
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService
    ) {
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
    }

    // NEW -> SAMPLE_COLLECTED
    public DiagnosticOrderTest collectSample(Long testId) {
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = normalize(test.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.SAMPLE_COLLECTED);

        test.setProcessingStatus(DiagnosticStatus.SAMPLE_COLLECTED);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    // SAMPLE_COLLECTED -> ACCEPTED
    public DiagnosticOrderTest accept(Long testId, String acceptedBy) {
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = normalize(test.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.ACCEPTED);

        test.setProcessingStatus(DiagnosticStatus.ACCEPTED);
        test.setAcceptedBy(acceptedBy);
        test.setAcceptedDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    // ACCEPTED -> RESULT_READY
    public DiagnosticOrderTest markReady(Long testId) {
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = normalize(test.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.RESULT_READY);

        test.setProcessingStatus(DiagnosticStatus.RESULT_READY);
        test.setReadyDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    // RESULT_READY -> REVIEWED (اختياري)
    public DiagnosticOrderTest review(Long testId) {
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = normalize(test.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.REVIEWED);

        test.setProcessingStatus(DiagnosticStatus.REVIEWED);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    // RESULT_READY or REVIEWED -> RESULT_APPROVED
    public DiagnosticOrderTest approve(Long testId) {
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = normalize(test.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.RESULT_APPROVED);

        test.setProcessingStatus(DiagnosticStatus.RESULT_APPROVED);
        test.setApprovedDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }


    public DiagnosticOrderTest reject(Long testId, String rejectedBy, String rejectedReason) {
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = normalize(test.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.REJECTED);

        test.setProcessingStatus(DiagnosticStatus.REJECTED);
        test.setRejectedBy(rejectedBy);
        test.setRejectedReason(rejectedReason);
        test.setRejectedDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    // NEW or SAMPLE_COLLECTED or ACCEPTED -> CANCELLED (قرر حسب البزنس)
    public DiagnosticOrderTest cancel(Long testId, String cancelledBy, String cancellationReason) {
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticOrderTestStatus current = test.getStatus() == null ? DiagnosticOrderTestStatus.NEW : test.getStatus();
        if (current == DiagnosticOrderTestStatus.CANCELLED) {
            throw new BadRequestAlertException("Already cancelled", "diagnostic_order_tests", "already_cancelled");
        }

        test.setStatus(DiagnosticOrderTestStatus.CANCELLED);
        test.setCancelledBy(cancelledBy);
        test.setCancelledDate(Instant.now());
        test.setCancellationReason(cancellationReason);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    private DiagnosticOrderTest getTest(Long testId) {
        return diagnosticOrderTestRepository.findById(testId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "DiagnosticOrderTest not found with id " + testId,
                        "diagnostic_order_tests",
                        "notfound"
                ));
    }

    private DiagnosticStatus normalize(DiagnosticStatus status) {
        return status == null ? DiagnosticStatus.NEW : status;
    }

    private void ensureTransition(DiagnosticStatus from, DiagnosticStatus to) {

        if (to == DiagnosticStatus.SAMPLE_COLLECTED) {
            if (from != DiagnosticStatus.NEW) throw invalid(from, to);
            return;
        }

        if (to == DiagnosticStatus.ACCEPTED) {
            if (from != DiagnosticStatus.SAMPLE_COLLECTED) throw invalid(from, to);
            return;
        }

        if (to == DiagnosticStatus.RESULT_READY) {
            if (from != DiagnosticStatus.ACCEPTED) throw invalid(from, to);
            return;
        }

        if (to == DiagnosticStatus.REVIEWED) {
            if (from != DiagnosticStatus.RESULT_READY) throw invalid(from, to);
            return;
        }

        if (to == DiagnosticStatus.RESULT_APPROVED) {
            if (!(from == DiagnosticStatus.RESULT_READY || from == DiagnosticStatus.REVIEWED)) throw invalid(from, to);
            return;
        }

        if (to == DiagnosticStatus.REJECTED) {
            if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.SAMPLE_COLLECTED)) throw invalid(from, to);
            return;
        }

    }

    private BadRequestAlertException invalid(DiagnosticStatus from, DiagnosticStatus to) {
        return new BadRequestAlertException(
                "Invalid transition " + from + " -> " + to,
                "diagnostic_order_tests",
                "invalid_transition"
        );
    }
}
