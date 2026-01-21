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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        DiagnosticOrderTest test = getTest(testId);

        // Normalize null status to NEW then validate transition
        DiagnosticStatus from = normalize(test.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.SAMPLE_COLLECTED);

        test.setProcessingStatus(DiagnosticStatus.SAMPLE_COLLECTED);

        // Persist and recompute aggregated statuses for the parent order
        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
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

    /**
     * Transition: {@code ACCEPTED -> RESULT_READY}.
     * <p>
     * Sets processingStatus to {@link DiagnosticStatus#RESULT_READY} and stamps readyDate.
     *
     * @param testId id of the DiagnosticOrderTest
     * @return updated and persisted entity
     */
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

    /**
     * Transition: {@code RESULT_READY -> REVIEWED} (optional step).
     * <p>
     * Sets processingStatus to {@link DiagnosticStatus#REVIEWED}.
     *
     * @param testId id of the DiagnosticOrderTest
     * @return updated and persisted entity
     */
    public DiagnosticOrderTest review(Long testId) {
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = normalize(test.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.REVIEWED);

        test.setProcessingStatus(DiagnosticStatus.REVIEWED);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
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
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = normalize(test.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.RESULT_APPROVED);

        test.setProcessingStatus(DiagnosticStatus.RESULT_APPROVED);
        test.setApprovedDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
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
        return saved;
    }
    /**
     * Bulk Transition: {@code SAMPLE_COLLECTED -> ACCEPTED} for multiple tests.
     * <p>
     * This method applies the same acceptance workflow as {@link #accept(Long, String)} but in bulk:
     * <ul>
     *   <li>Loads each {@link DiagnosticOrderTest} by id.</li>
     *   <li>Validates the workflow transition using {@link #ensureTransition(DiagnosticStatus, DiagnosticStatus)}.</li>
     *   <li>Sets {@link DiagnosticStatus#ACCEPTED} as processingStatus.</li>
     *   <li>Fills acceptance audit fields: acceptedBy + acceptedDate.</li>
     *   <li>Persists each updated test.</li>
     *   <li>Recomputes aggregated Lab/Radiology statuses once per parent order (optimized).</li>
     * </ul>
     *
     * Transaction behavior:
     * <ul>
     *   <li>Fail-fast: if any test id is invalid or transition is not allowed, an exception is thrown and
     *       the entire transaction is rolled back.</li>
     * </ul>
     *
     * @param testIds     list of DiagnosticOrderTest ids to accept
     * @param acceptedBy  username/userId performing the accept action
     */
    public void bulkAccept(List<Long> testIds, String acceptedBy) {
        Set<Long> orderIds = new HashSet<>();

        for (Long id : testIds) {
            DiagnosticOrderTest test = getTest(id);

            DiagnosticStatus from = normalize(test.getProcessingStatus());
            ensureTransition(from, DiagnosticStatus.ACCEPTED);

            test.setProcessingStatus(DiagnosticStatus.ACCEPTED);
            test.setAcceptedBy(acceptedBy);
            test.setAcceptedDate(Instant.now());

            DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
            orderIds.add(saved.getOrderId());
        }

        for (Long orderId : orderIds) {
            diagnosticOrderStatusService.recomputeLabRadStatuses(orderId);
        }
    }
    /**
     * Bulk Transition: to {@link DiagnosticStatus#REJECTED} for multiple tests.
     * <p>
     * This method applies the same rejection workflow as {@link #reject(Long, String, String)} but in bulk:
     * <ul>
     *   <li>Loads each {@link DiagnosticOrderTest} by id.</li>
     *   <li>Validates the workflow transition using {@link #ensureTransition(DiagnosticStatus, DiagnosticStatus)}.</li>
     *   <li>Sets {@link DiagnosticStatus#REJECTED} as processingStatus.</li>
     *   <li>Fills rejection audit fields: rejectedBy + rejectedDate + rejectedReason.</li>
     *   <li>Persists each updated test.</li>
     *   <li>Recomputes aggregated Lab/Radiology statuses once per parent order (optimized).</li>
     * </ul>
     *
     * Transaction behavior:
     * <ul>
     *   <li>Fail-fast: if any test id is invalid or transition is not allowed, an exception is thrown and
     *       the entire transaction is rolled back.</li>
     * </ul>
     *
     * @param testIds         list of DiagnosticOrderTest ids to reject
     * @param rejectedBy      username/userId performing the reject action
     * @param rejectedReason  mandatory textual reason for rejection
     */
    public void bulkReject(List<Long> testIds, String rejectedBy, String rejectedReason) {
        Set<Long> orderIds = new HashSet<>();

        for (Long id : testIds) {
            DiagnosticOrderTest test = getTest(id);

            DiagnosticStatus from = normalize(test.getProcessingStatus());
            ensureTransition(from, DiagnosticStatus.REJECTED);

            test.setProcessingStatus(DiagnosticStatus.REJECTED);
            test.setRejectedBy(rejectedBy);
            test.setRejectedReason(rejectedReason);
            test.setRejectedDate(Instant.now());

            DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
            orderIds.add(saved.getOrderId());
        }

        for (Long orderId : orderIds) {
            diagnosticOrderStatusService.recomputeLabRadStatuses(orderId);
        }
    }


    /**
     * Loads a DiagnosticOrderTest by id or throws a {@link BadRequestAlertException} if not found.
     *
     * @param testId id of the DiagnosticOrderTest
     * @return loaded entity
     */
    private DiagnosticOrderTest getTest(Long testId) {
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
    private DiagnosticStatus normalize(DiagnosticStatus status) {
        return status == null ? DiagnosticStatus.NEW : status;
    }

    /**
     * Validates whether a transition from {@code from} to {@code to} is allowed.
     * <p>
     * Throws {@link BadRequestAlertException} on invalid transitions.
     *
     * @param from current (normalized) processing status
     * @param to target processing status
     */
    private void ensureTransition(DiagnosticStatus from, DiagnosticStatus to) {

        // NEW or SAMPLE_COLLECTED -> SAMPLE_COLLECTED (idempotent)
        if (to == DiagnosticStatus.SAMPLE_COLLECTED) {
            if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.SAMPLE_COLLECTED)) {
                throw invalid(from, to);
            }
            return;
        }

        // SAMPLE_COLLECTED -> ACCEPTED
        if (to == DiagnosticStatus.ACCEPTED) {
            if (from != DiagnosticStatus.SAMPLE_COLLECTED) throw invalid(from, to);
            return;
        }

        // ACCEPTED -> RESULT_READY
        if (to == DiagnosticStatus.RESULT_READY) {
            if (from != DiagnosticStatus.ACCEPTED) throw invalid(from, to);
            return;
        }

        // RESULT_READY -> REVIEWED
        if (to == DiagnosticStatus.REVIEWED) {
            if (from != DiagnosticStatus.RESULT_READY) throw invalid(from, to);
            return;
        }

        // RESULT_READY or REVIEWED -> RESULT_APPROVED
        if (to == DiagnosticStatus.RESULT_APPROVED) {
            if (!(from == DiagnosticStatus.RESULT_READY || from == DiagnosticStatus.REVIEWED)) throw invalid(from, to);
            return;
        }

        // Allowed rejection paths (as coded): NEW or SAMPLE_COLLECTED -> REJECTED
        if (to == DiagnosticStatus.REJECTED) {
            if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.SAMPLE_COLLECTED)) throw invalid(from, to);
            return;
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
        return new BadRequestAlertException(
                "invalid_transition",
                "diagnostic_order_tests",
                "Invalid transition " + from + " -> " + to
        );
    }
}
