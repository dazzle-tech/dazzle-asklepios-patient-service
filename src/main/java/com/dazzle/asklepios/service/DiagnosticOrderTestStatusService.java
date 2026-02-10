// src/main/java/com/dazzle/asklepios/service/DiagnosticOrderTestStatusService.java
package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service responsible for managing the lifecycle (status transitions) of a {@link DiagnosticOrderTest}.
 * <p>
 * This class enforces allowed transitions for {@link DiagnosticStatus} (processingStatus) and updates
 * audit fields (acceptedBy/date, readyDate, approvedDate, rejectedBy/date/reason, etc.).
 * <p>
 * After each successful transition, it triggers recomputation of the aggregated Lab/Radiology statuses
 * for the parent order via {@link DiagnosticOrderStatusService}.
 */
@Service
@Transactional
public class DiagnosticOrderTestStatusService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestStatusService.class);

    /** Repository for persisting and loading DiagnosticOrderTest entities. */
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    /** Service used to recompute overall/aggregated statuses for the parent diagnostic order. */
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;

    /**
     * Constructs the status service with required dependencies.
     *
     * @param diagnosticOrderTestRepository repository for DiagnosticOrderTest persistence
     * @param diagnosticOrderStatusService service that recomputes parent order statuses
     */
    public DiagnosticOrderTestStatusService(
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService
    ) {
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
    }

    /**
     * Transition: {@code NEW -> SAMPLE_COLLECTED}.
     * <p>
     * Updates the test's processingStatus to {@link DiagnosticStatus#SAMPLE_COLLECTED}.
     *
     * @param testId id of the DiagnosticOrderTest
     * @return updated and persisted entity
     */
    public DiagnosticOrderTest collectSample(Long testId) {
        LOG.debug("[DiagnosticOrderTestStatus] COLLECT_SAMPLE - start. testId={}", testId);
        DiagnosticOrderTest test = getTest(testId);

        // Normalize null status to NEW then validate transition
        DiagnosticStatus from = test.getProcessingStatus();
        ensureTransition(from, DiagnosticStatus.SAMPLE_COLLECTED);

        test.setProcessingStatus(DiagnosticStatus.SAMPLE_COLLECTED);

        // Persist and recompute aggregated statuses for the parent order
        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        LOG.debug("[DiagnosticOrderTestStatus] COLLECT_SAMPLE - done. testId={} orderId={} status={}",
                saved.getId(), saved.getOrderId(), saved.getProcessingStatus());
        return saved;
    }

    /**
     * Transition: {@code SAMPLE_COLLECTED -> ACCEPTED}.
     * <p>
     * Sets processingStatus to {@link DiagnosticStatus#ACCEPTED} and fills acceptance audit fields.
     *
     * @param testId id of the DiagnosticOrderTest
     * @param acceptedBy username/userId who accepted the test
     * @return updated and persisted entity
     */
    public DiagnosticOrderTest accept(Long testId, String acceptedBy) {
        LOG.debug("[DiagnosticOrderTestStatus] ACCEPT - start. testId={} acceptedBy={}", testId, acceptedBy);
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = test.getProcessingStatus();
        ensureTransition(from, DiagnosticStatus.ACCEPTED);

        test.setProcessingStatus(DiagnosticStatus.ACCEPTED);
        test.setAcceptedBy(acceptedBy);
        test.setAcceptedDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        LOG.debug("[DiagnosticOrderTestStatus] ACCEPT - done. testId={} orderId={} status={} acceptedBy={}",
                saved.getId(), saved.getOrderId(), saved.getProcessingStatus(), saved.getAcceptedBy());
        return saved;
    }

    /**
     * Transition: {@code ACCEPTED -> RESULT_READY}.
     * <p>
     * Sets processingStatus to {@link DiagnosticStatus#RESULT_READY} and stamps readyDate.
     *
     * @param testId id of the DiagnosticOrderTest
     * @return updated and persisted entity
     */
    public DiagnosticOrderTest markReady(Long testId) {
        LOG.debug("[DiagnosticOrderTestStatus] MARK_READY - start. testId={}", testId);
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = test.getProcessingStatus();
        ensureTransition(from, DiagnosticStatus.RESULT_READY);

        test.setProcessingStatus(DiagnosticStatus.RESULT_READY);
        test.setReadyDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        LOG.debug("[DiagnosticOrderTestStatus] MARK_READY - done. testId={} orderId={} status={}",
                saved.getId(), saved.getOrderId(), saved.getProcessingStatus());
        return saved;
    }

    /**
     * Transition: {@code RESULT_READY -> REVIEWED} (optional step).
     * <p>
     * Sets processingStatus to {@link DiagnosticStatus#REVIEWED}.
     *
     * @param testId id of the DiagnosticOrderTest
     * @return updated and persisted entity
     */
    public DiagnosticOrderTest review(Long testId) {
        LOG.debug("[DiagnosticOrderTestStatus] REVIEW - start. testId={}", testId);
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = test.getProcessingStatus();
        ensureTransition(from, DiagnosticStatus.REVIEWED);

        test.setProcessingStatus(DiagnosticStatus.REVIEWED);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        LOG.debug("[DiagnosticOrderTestStatus] REVIEW - done. testId={} orderId={} status={}",
                saved.getId(), saved.getOrderId(), saved.getProcessingStatus());
        return saved;
    }

    /**
     * Transition: {@code RESULT_READY or REVIEWED -> RESULT_APPROVED}.
     * <p>
     * Sets processingStatus to {@link DiagnosticStatus#RESULT_APPROVED} and stamps approvedDate.
     *
     * @param testId id of the DiagnosticOrderTest
     * @return updated and persisted entity
     */
    public DiagnosticOrderTest approve(Long testId) {
        LOG.debug("[DiagnosticOrderTestStatus] APPROVE - start. testId={}", testId);
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = test.getProcessingStatus();
        ensureTransition(from, DiagnosticStatus.RESULT_APPROVED);

        test.setProcessingStatus(DiagnosticStatus.RESULT_APPROVED);
        test.setApprovedDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        LOG.debug("[DiagnosticOrderTestStatus] APPROVE - done. testId={} orderId={} status={}",
                saved.getId(), saved.getOrderId(), saved.getProcessingStatus());
        return saved;
    }

    /**
     * Transition: allowed to {@link DiagnosticStatus#REJECTED} based on business rules.
     * <p>
     * Sets processingStatus to REJECTED and fills rejection audit fields (by/reason/date).
     *
     * @param testId id of the DiagnosticOrderTest
     * @param rejectedBy username/userId who rejected the test
     * @param rejectedReason textual reason for rejection
     * @return updated and persisted entity
     */
    public DiagnosticOrderTest reject(Long testId, String rejectedBy, String rejectedReason) {
        LOG.debug("[DiagnosticOrderTestStatus] REJECT - start. testId={} rejectedBy={} reason={}",
                testId, rejectedBy, rejectedReason);
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = test.getProcessingStatus();
        ensureTransition(from, DiagnosticStatus.REJECTED);

        test.setProcessingStatus(DiagnosticStatus.REJECTED);
        test.setRejectedBy(rejectedBy);
        test.setRejectedReason(rejectedReason);
        test.setRejectedDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        LOG.debug("[DiagnosticOrderTestStatus] REJECT - done. testId={} orderId={} status={} rejectedBy={}",
                saved.getId(), saved.getOrderId(), saved.getProcessingStatus(), saved.getRejectedBy());
        return saved;
    }

    /**
     * Cancels the DiagnosticOrderTest at the entity level (uses {@link DiagnosticOrderTestStatus}).
     * <p>
     * Business rule: if already cancelled, throws an error.
     * <p>
     * Note: This method changes {@code test.status} (not processingStatus).
     *
     * @param testId id of the DiagnosticOrderTest
     * @param cancelledBy username/userId who cancelled the test
     * @param cancellationReason textual reason for cancellation
     * @return updated and persisted entity
     */
    public DiagnosticOrderTest cancel(Long testId, String cancelledBy, String cancellationReason) {
        LOG.debug("[DiagnosticOrderTestStatus] CANCEL - start. testId={} cancelledBy={} reason={}",
                testId, cancelledBy, cancellationReason);
        DiagnosticOrderTest test = getTest(testId);

        // Treat null as NEW for entity-level status
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
        LOG.debug("[DiagnosticOrderTestStatus] CANCEL - done. testId={} orderId={} status={} cancelledBy={}",
                saved.getId(), saved.getOrderId(), saved.getStatus(), saved.getCancelledBy());
        return saved;
    }

    /**
     * Loads a DiagnosticOrderTest by id or throws a {@link BadRequestAlertException} if not found.
     *
     * @param testId id of the DiagnosticOrderTest
     * @return loaded entity
     */
    private DiagnosticOrderTest getTest(Long testId) {
        LOG.debug("[DiagnosticOrderTestStatus] GET_TEST - testId={}", testId);
        return diagnosticOrderTestRepository.findById(testId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + testId
                ));
    }

    /**
     * Normalizes null processingStatus to {@link DiagnosticStatus#NEW}.
     *
     * @param status current processing status (may be null)
     * @return normalized status
     */
    /**
     * Validates whether a transition from {@code from} to {@code to} is allowed.
     * <p>
     * Throws {@link BadRequestAlertException} on invalid transitions.
     *
     * @param from current (normalized) processing status
     * @param to target processing status
     */
    private void ensureTransition(DiagnosticStatus from, DiagnosticStatus to) {
        LOG.debug("[DiagnosticOrderTestStatus] ENSURE_TRANSITION - from={} to={}", from, to);
        switch (to) {
            case SAMPLE_COLLECTED -> {
                if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.SAMPLE_COLLECTED)) {
                    throw invalid(from, to);
                }
            }
            case ACCEPTED -> {
                if (from != DiagnosticStatus.SAMPLE_COLLECTED) {
                    throw invalid(from, to);
                }
            }
            case RESULT_READY -> {
                if (from != DiagnosticStatus.ACCEPTED) {
                    throw invalid(from, to);
                }
            }
            case REVIEWED -> {
                if (from != DiagnosticStatus.RESULT_READY) {
                    throw invalid(from, to);
                }
            }
            case RESULT_APPROVED -> {
                if (!(from == DiagnosticStatus.RESULT_READY || from == DiagnosticStatus.REVIEWED)) {
                    throw invalid(from, to);
                }
            }
            case REJECTED -> {
                if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.SAMPLE_COLLECTED)) {
                    throw invalid(from, to);
                }
            }
            default -> {
                // No-op: handled by specific cases only
            }
        }
    }

    /**
     * Builds a standard invalid-transition exception.
     *
     * @param from current processing status
     * @param to target processing status
     * @return exception describing the invalid transition
     */
    private BadRequestAlertException invalid(DiagnosticStatus from, DiagnosticStatus to) {
        LOG.debug("[DiagnosticOrderTestStatus] INVALID_TRANSITION - from={} to={}", from, to);
        return new BadRequestAlertException(
                "invalid_transition",
                "diagnostic_order_tests",
                "Invalid transition " + from + " -> " + to
        );
    }
}
