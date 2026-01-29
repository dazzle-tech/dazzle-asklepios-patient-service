package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
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

    /**
     * Repository for persisting and loading DiagnosticOrderTest entities.
     */
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    /**
     * Service used to recompute overall/aggregated statuses for the parent diagnostic order.
     */
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;

    /**
     * Constructs the status service with required dependencies.
     *
     * @param diagnosticOrderTestRepository repository for DiagnosticOrderTest persistence
     * @param diagnosticOrderStatusService  service that recomputes parent order statuses
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

        ensureTransition(test, DiagnosticStatus.SAMPLE_COLLECTED);

        test.setProcessingStatus(DiagnosticStatus.SAMPLE_COLLECTED);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    /**
     * Radiology-only event: patient arrival.
     * <p>
     * This does not change processingStatus; it records arrival metadata used as a prerequisite for acceptance.
     *
     * @param testId id of the DiagnosticOrderTest
     * @param dto    arrival payload (date and optional note)
     * @return updated and persisted entity
     */
    public DiagnosticOrderTest patientArrived(Long testId, com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.patientarrived.PatientArrivedUpdateRequestDTO dto) {
        DiagnosticOrderTest test = getTest(testId);

        if (test.getOrderType() != TestType.RADIOLOGY) {
            throw new BadRequestAlertException("not_radiology", "diagnostic_order_tests", "Test is not radiology");
        }

        test.setPatientArrivedDate(dto.patientArrivedDate() != null ? dto.patientArrivedDate() : Instant.now());
        test.setPatientArrivedNoteRad(dto.patientArrivedNoteRad());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    /**
     * Transition:
     * <ul>
     *   <li>Laboratory: {@code SAMPLE_COLLECTED -> ACCEPTED}</li>
     *   <li>Radiology: requires {@code patientArrivedDate != null}</li>
     * </ul>
     * <p>
     * Sets processingStatus to {@link DiagnosticStatus#ACCEPTED} and fills acceptance audit fields.
     *
     * @param testId     id of the DiagnosticOrderTest
     * @param acceptedBy username/userId who accepted the test
     * @return updated and persisted entity
     */
    public DiagnosticOrderTest accept(Long testId, String acceptedBy) {
        DiagnosticOrderTest test = getTest(testId);

        ensureTransition(test, DiagnosticStatus.ACCEPTED);

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

        ensureTransition(test, DiagnosticStatus.RESULT_READY);

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

        ensureTransition(test, DiagnosticStatus.REVIEWED);

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

        ensureTransition(test, DiagnosticStatus.RESULT_APPROVED);

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
     * @param testId         id of the DiagnosticOrderTest
     * @param rejectedBy     username/userId who rejected the test
     * @param rejectedReason textual reason for rejection
     * @return updated and persisted entity
     */
    public DiagnosticOrderTest reject(Long testId, String rejectedBy, String rejectedReason) {
        DiagnosticOrderTest test = getTest(testId);

        ensureTransition(test, DiagnosticStatus.REJECTED);

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
     * @param testId             id of the DiagnosticOrderTest
     * @param cancelledBy        username/userId who cancelled the test
     * @param cancellationReason textual reason for cancellation
     * @return updated and persisted entity
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
     * Validates whether a transition to {@code to} is allowed for the given test.
     * <p>
     * Rules:
     * <ul>
     *   <li>Laboratory: follows the original SAMPLE_COLLECTED-based workflow.</li>
     *   <li>Radiology: ACCEPTED requires patientArrivedDate; SAMPLE_COLLECTED is not allowed.</li>
     * </ul>
     *
     * @param test target test
     * @param to   target processing status
     */
    private void ensureTransition(DiagnosticOrderTest test, DiagnosticStatus to) {
        DiagnosticStatus from = normalize(test.getProcessingStatus());
        TestType type = test.getOrderType();

        if (to == DiagnosticStatus.SAMPLE_COLLECTED) {
            if (type == TestType.RADIOLOGY) throw invalid(from, to);
            if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.SAMPLE_COLLECTED)) throw invalid(from, to);
            return;
        }

        if (to == DiagnosticStatus.ACCEPTED) {
            if (type == TestType.RADIOLOGY) {
                if (test.getPatientArrivedDate() == null) throw invalid(from, to);
                return;
            }
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
            if (type == TestType.RADIOLOGY) {
                if (test.getPatientArrivedDate() != null) throw invalid(from, to);
                if (from != DiagnosticStatus.NEW) throw invalid(from, to);
                return;
            }
            if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.SAMPLE_COLLECTED)) throw invalid(from, to);
            return;
        }
    }

    /**
     * Builds a standard invalid-transition exception.
     *
     * @param from current processing status
     * @param to   target processing status
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
