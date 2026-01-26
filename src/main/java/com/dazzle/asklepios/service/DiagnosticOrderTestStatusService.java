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
 *
 * <p>This class enforces allowed transitions for {@link DiagnosticStatus} (processingStatus) and updates
 * audit fields (acceptedBy/date, readyDate, approvedDate, rejectedBy/date/reason, etc.).</p>
 *
 * <p>After each successful transition, it triggers recomputation of the aggregated Lab/Radiology statuses
 * for the parent order via {@link DiagnosticOrderStatusService}.</p>
 */
@Service
@Transactional
public class DiagnosticOrderTestStatusService {

    /**
     * Repository for persisting and loading {@link DiagnosticOrderTest} entities.
     */
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    /**
     * Service used to recompute overall/aggregated statuses for the parent diagnostic order.
     */
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;

    /**
     * Constructs the status service with required dependencies.
     *
     * @param diagnosticOrderTestRepository repository for {@link DiagnosticOrderTest} persistence
     * @param diagnosticOrderStatusService  service that recomputes parent order statuses
     */
    public DiagnosticOrderTestStatusService(
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService
    ) {
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
    }

    // ---------------------------------------------------------------------
    // Standard transitions
    // ---------------------------------------------------------------------

    /**
     * Moves the test processing status to {@link DiagnosticStatus#SAMPLE_COLLECTED}.
     *
     * @param testId test id
     * @return updated test
     * @throws BadRequestAlertException if the test does not exist or the transition is not allowed
     */
    public DiagnosticOrderTest collectSample(Long testId) {
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = normalize(test.getProcessingStatus());
        ensureTransition(from, DiagnosticStatus.SAMPLE_COLLECTED);

        test.setProcessingStatus(DiagnosticStatus.SAMPLE_COLLECTED);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    /**
     * Moves the test processing status to {@link DiagnosticStatus#ACCEPTED} and stores acceptance audit fields.
     *
     * @param testId     test id
     * @param acceptedBy username or identifier of the user who accepted the test
     * @return updated test
     * @throws BadRequestAlertException if the test does not exist or the transition is not allowed
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
     * Moves the test processing status to {@link DiagnosticStatus#RESULT_READY} and sets the ready timestamp.
     *
     * @param testId test id
     * @return updated test
     * @throws BadRequestAlertException if the test does not exist or the transition is not allowed
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
     * Moves the test processing status to {@link DiagnosticStatus#REVIEWED}.
     *
     * @param testId test id
     * @return updated test
     * @throws BadRequestAlertException if the test does not exist or the transition is not allowed
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
     * Moves the test processing status to {@link DiagnosticStatus#RESULT_APPROVED} and sets the approval timestamp.
     *
     * @param testId test id
     * @return updated test
     * @throws BadRequestAlertException if the test does not exist or the transition is not allowed
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
     * Moves the test processing status to {@link DiagnosticStatus#REJECTED} and stores rejection audit fields.
     *
     * @param testId         test id
     * @param rejectedBy     username or identifier of the user who rejected the test
     * @param rejectedReason rejection reason text
     * @return updated test
     * @throws BadRequestAlertException if the test does not exist or the transition is not allowed
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
     * Cancels the test at the entity-level status ({@link DiagnosticOrderTestStatus}) and stores cancellation audit fields.
     *
     * <p>This does not modify {@link DiagnosticStatus} (processingStatus).</p>
     *
     * @param testId             test id
     * @param cancelledBy        username or identifier of the user who cancelled the test
     * @param cancellationReason cancellation reason text
     * @return updated test
     * @throws BadRequestAlertException if the test is already cancelled or does not exist
     */
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

    // ---------------------------------------------------------------------
    // Undo Accept
    // ---------------------------------------------------------------------

    /**
     * Undo accept for a test currently in {@link DiagnosticStatus#ACCEPTED}.
     *
     * <p>Resets the processing status back to {@link DiagnosticStatus#NEW} and clears
     * acceptance audit fields.</p>
     *
     * @param testId test id
     * @return updated test
     * @throws BadRequestAlertException if the test is not currently ACCEPTED
     */
    public DiagnosticOrderTest undoAccept(Long testId) {
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticStatus from = normalize(test.getProcessingStatus());
        if (from != DiagnosticStatus.ACCEPTED) {
            throw new BadRequestAlertException(
                    "invalid_transition",
                    "diagnostic_order_tests",
                    "Undo accept is allowed only from ACCEPTED, current=" + from
            );
        }

        test.setProcessingStatus(DiagnosticStatus.NEW);
        test.setAcceptedBy(null);
        test.setAcceptedDate(null);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    // ---------------------------------------------------------------------
    // Bulk actions
    // ---------------------------------------------------------------------

    /**
     * Accepts multiple tests in a single call.
     *
     * <p>For each test: sets {@link DiagnosticStatus#ACCEPTED}, fills acceptance audit fields, saves it,
     * then recomputes parent order aggregated statuses once per affected order.</p>
     *
     * @param testIds    list of test ids to accept
     * @param acceptedBy username or identifier of the user who accepted the tests
     * @throws BadRequestAlertException if any test does not exist or any transition is not allowed
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
     * Rejects multiple tests in a single call.
     *
     * <p>For each test: sets {@link DiagnosticStatus#REJECTED}, fills rejection audit fields, saves it,
     * then recomputes parent order aggregated statuses once per affected order.</p>
     *
     * @param testIds        list of test ids to reject
     * @param rejectedBy     username or identifier of the user who rejected the tests
     * @param rejectedReason rejection reason text
     * @throws BadRequestAlertException if any test does not exist or any transition is not allowed
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

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Loads a {@link DiagnosticOrderTest} by id or throws a {@link BadRequestAlertException}.
     *
     * @param testId test id
     * @return loaded test
     * @throws BadRequestAlertException if no test exists with the given id
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
     * Normalizes a nullable {@link DiagnosticStatus} to a non-null value.
     *
     * @param status current processing status (may be null)
     * @return {@link DiagnosticStatus#NEW} if status is null; otherwise the provided status
     */
    private DiagnosticStatus normalize(DiagnosticStatus status) {
        return status == null ? DiagnosticStatus.NEW : status;
    }

    /**
     * Validates that the transition from {@code from} to {@code to} is allowed.
     *
     * @param from current processing status (non-null)
     * @param to   requested processing status
     * @throws BadRequestAlertException if the transition is not allowed
     */
    private void ensureTransition(DiagnosticStatus from, DiagnosticStatus to) {

        if (to == DiagnosticStatus.SAMPLE_COLLECTED) {
            if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.SAMPLE_COLLECTED)) {
                throw invalid(from, to);
            }
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

    /**
     * Builds a standardized {@link BadRequestAlertException} for invalid status transitions.
     *
     * @param from current processing status
     * @param to   requested processing status
     * @return exception instance describing the invalid transition
     */
    private BadRequestAlertException invalid(DiagnosticStatus from, DiagnosticStatus to) {
        return new BadRequestAlertException(
                "invalid_transition",
                "diagnostic_order_tests",
                "Invalid transition " + from + " -> " + to
        );
    }
}
