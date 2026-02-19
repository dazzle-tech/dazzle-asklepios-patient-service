package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.patientarrived.PatientArrivedCreateRequestDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.PatientArrivedResponseVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestStatusService.class);

    /**
     * Repository for persisting and loading DiagnosticOrderTest entities.
     */
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    /**
     * Service used to recompute overall/aggregated statuses for the parent diagnostic order.
     */
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;

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

    public DiagnosticOrderTest collectSample(Long testId) {
        LOG.debug("[DiagnosticOrderTestStatus] COLLECT_SAMPLE - start. testId={}", testId);
        DiagnosticOrderTest test = getTest(testId);

        ensureTransition(test, DiagnosticStatus.SAMPLE_COLLECTED);

        test.setProcessingStatus(DiagnosticStatus.SAMPLE_COLLECTED);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        LOG.debug("[DiagnosticOrderTestStatus] COLLECT_SAMPLE - done. testId={} orderId={} status={}",
                saved.getId(), saved.getOrderId(), saved.getProcessingStatus());
        return saved;
    }

    /**
     * Radiology-only: patient arrival. Sets processingStatus to PATIENT_ARRIVED and stores arrival metadata.
     */
    public PatientArrivedResponseVM patientArrived(Long testId, PatientArrivedCreateRequestDTO dto) {
        DiagnosticOrderTest test = getTest(testId);

        if (test.getOrderType() != TestType.RADIOLOGY) {
            throw new BadRequestAlertException("not_radiology", "diagnostic_order_tests", "Test is not radiology");
        }

        ensureTransition(test, DiagnosticStatus.PATIENT_ARRIVED);

        test.setPatientArrivedDate(dto.patientArrivedDate() != null ? dto.patientArrivedDate() : Instant.now());
        test.setPatientArrivedNoteRad(dto.patientArrivedNoteRad());
        test.setProcessingStatus(DiagnosticStatus.PATIENT_ARRIVED);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return new PatientArrivedResponseVM(
                saved.getId(),
                saved.getPatientArrivedDate(),
                saved.getPatientArrivedNoteRad()
        );
    }

    public DiagnosticOrderTest accept(Long testId, String acceptedBy) {
        LOG.debug("[DiagnosticOrderTestStatus] ACCEPT - start. testId={} acceptedBy={}", testId, acceptedBy);
        DiagnosticOrderTest test = getTest(testId);

        ensureTransition(test, DiagnosticStatus.ACCEPTED);

        test.setProcessingStatus(DiagnosticStatus.ACCEPTED);
        test.setAcceptedBy(acceptedBy);
        test.setAcceptedDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        LOG.debug("[DiagnosticOrderTestStatus] ACCEPT - done. testId={} orderId={} status={} acceptedBy={}",
                saved.getId(), saved.getOrderId(), saved.getProcessingStatus(), saved.getAcceptedBy());
        return saved;
    }

    public DiagnosticOrderTest markReady(Long testId) {
        LOG.debug("[DiagnosticOrderTestStatus] MARK_READY - start. testId={}", testId);
        DiagnosticOrderTest test = getTest(testId);

        ensureTransition(test, DiagnosticStatus.RESULT_READY);

        test.setProcessingStatus(DiagnosticStatus.RESULT_READY);
        test.setReadyDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        LOG.debug("[DiagnosticOrderTestStatus] MARK_READY - done. testId={} orderId={} status={}",
                saved.getId(), saved.getOrderId(), saved.getProcessingStatus());
        return saved;
    }

    public DiagnosticOrderTest review(Long testId) {
        LOG.debug("[DiagnosticOrderTestStatus] REVIEW - start. testId={}", testId);
        DiagnosticOrderTest test = getTest(testId);

        ensureTransition(test, DiagnosticStatus.REVIEWED);

        test.setProcessingStatus(DiagnosticStatus.REVIEWED);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        LOG.debug("[DiagnosticOrderTestStatus] REVIEW - done. testId={} orderId={} status={}",
                saved.getId(), saved.getOrderId(), saved.getProcessingStatus());
        return saved;
    }

    public DiagnosticOrderTest approve(Long testId) {
        LOG.debug("[DiagnosticOrderTestStatus] APPROVE - start. testId={}", testId);
        DiagnosticOrderTest test = getTest(testId);

        ensureTransition(test, DiagnosticStatus.RESULT_APPROVED);

        test.setProcessingStatus(DiagnosticStatus.RESULT_APPROVED);
        test.setApprovedDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        LOG.debug("[DiagnosticOrderTestStatus] APPROVE - done. testId={} orderId={} status={}",
                saved.getId(), saved.getOrderId(), saved.getProcessingStatus());
        return saved;
    }

    public DiagnosticOrderTest reject(Long testId, String rejectedBy, String rejectedReason) {
        LOG.debug("[DiagnosticOrderTestStatus] REJECT - start. testId={} rejectedBy={} reason={}",
                testId, rejectedBy, rejectedReason);
        DiagnosticOrderTest test = getTest(testId);

        ensureTransition(test, DiagnosticStatus.REJECTED);

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

    public DiagnosticOrderTest cancel(Long testId, String cancelledBy, String cancellationReason) {
        LOG.debug("[DiagnosticOrderTestStatus] CANCEL - start. testId={} cancelledBy={} reason={}",
                testId, cancelledBy, cancellationReason);
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
        LOG.debug("[DiagnosticOrderTestStatus] CANCEL - done. testId={} orderId={} status={} cancelledBy={}",
                saved.getId(), saved.getOrderId(), saved.getStatus(), saved.getCancelledBy());
        return saved;
    }

    // ---------------------------------------------------------------------
    // Undo Accept
    // ---------------------------------------------------------------------

    public DiagnosticOrderTest undoAccept(Long testId) {
        DiagnosticOrderTest test = getTest(testId);

        if (test.getProcessingStatus() != DiagnosticStatus.ACCEPTED) {
            throw new BadRequestAlertException(
                    "invalid_transition",
                    "diagnostic_order_tests",
                    "Undo accept is allowed only from ACCEPTED, current=" + test.getProcessingStatus()
            );
        }

        // Radiology goes back to PATIENT_ARRIVED, Lab goes back to SAMPLE_COLLECTED
        if (test.getOrderType() == TestType.RADIOLOGY) {
            test.setProcessingStatus(DiagnosticStatus.PATIENT_ARRIVED);
        } else {
            test.setProcessingStatus(DiagnosticStatus.SAMPLE_COLLECTED);
        }

        test.setAcceptedBy(null);
        test.setAcceptedDate(null);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());
        return saved;
    }

    // ---------------------------------------------------------------------
    // Bulk actions
    // ---------------------------------------------------------------------

    public void bulkAccept(List<Long> testIds, String acceptedBy) {
        Set<Long> orderIds = new HashSet<>();

        for (Long id : testIds) {
            DiagnosticOrderTest test = getTest(id);

            ensureTransition(test, DiagnosticStatus.ACCEPTED);

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

    public void bulkReject(List<Long> testIds, String rejectedBy, String rejectedReason) {
        Set<Long> orderIds = new HashSet<>();

        for (Long id : testIds) {
            DiagnosticOrderTest test = getTest(id);

            ensureTransition(test, DiagnosticStatus.REJECTED);

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
    // Reads / Helpers
    // ---------------------------------------------------------------------

    private DiagnosticOrderTest getTest(Long testId) {
        LOG.debug("[DiagnosticOrderTestStatus] GET_TEST - testId={}", testId);
        return diagnosticOrderTestRepository.findById(testId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + testId
                ));
    }

    @Transactional(readOnly = true)
    public PatientArrivedResponseVM getPatientArrived(Long testId) {
        DiagnosticOrderTest test = diagnosticOrderTestRepository.findById(testId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound", "diagnostic_order_tests", "DiagnosticOrderTest not found with id " + testId
                ));

        return new PatientArrivedResponseVM(
                test.getId(),
                test.getPatientArrivedDate(),
                test.getPatientArrivedNoteRad()
        );
    }

    private DiagnosticStatus normalize(DiagnosticStatus status) {
        return status == null ? DiagnosticStatus.NEW : status;
    }

    /**
     * Validates whether a transition to {@code to} is allowed for the given test.
     * <p>
     * Rules:
     * - Laboratory: NEW -> SAMPLE_COLLECTED -> ACCEPTED -> RESULT_READY -> REVIEWED -> RESULT_APPROVED
     * - Radiology: NEW -> PATIENT_ARRIVED -> ACCEPTED -> RESULT_READY -> REVIEWED -> RESULT_APPROVED
     * - Radiology: SAMPLE_COLLECTED is not allowed
     */
    private void ensureTransition(DiagnosticOrderTest test, DiagnosticStatus to) {
        DiagnosticStatus from = normalize(test.getProcessingStatus());
        TestType type = test.getOrderType();

        LOG.debug("[DiagnosticOrderTestStatus] ENSURE_TRANSITION - type={} from={} to={}", type, from, to);

        if (to == DiagnosticStatus.SAMPLE_COLLECTED) {
            if (type == TestType.RADIOLOGY) throw invalid(from, to);
            if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.SAMPLE_COLLECTED)) throw invalid(from, to);
            return;
        }

        if (to == DiagnosticStatus.PATIENT_ARRIVED) {
            if (type != TestType.RADIOLOGY) throw invalid(from, to);
            if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.PATIENT_ARRIVED)) throw invalid(from, to);
            return;
        }

        if (to == DiagnosticStatus.ACCEPTED) {
            if (type == TestType.RADIOLOGY) {
                if (from != DiagnosticStatus.PATIENT_ARRIVED) throw invalid(from, to);
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
            if (!(from == DiagnosticStatus.RESULT_READY || from == DiagnosticStatus.PARTIALLY)) throw invalid(from, to);
            return;
        }

        if (to == DiagnosticStatus.REJECTED) {
            if (type == TestType.RADIOLOGY) {
                if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.PATIENT_ARRIVED))
                    throw invalid(from, to);
                return;
            }
            if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.SAMPLE_COLLECTED)) throw invalid(from, to);
        }
    }

    private BadRequestAlertException invalid(DiagnosticStatus from, DiagnosticStatus to) {
        LOG.debug("[DiagnosticOrderTestStatus] INVALID_TRANSITION - from={} to={}", from, to);
        return new BadRequestAlertException(
                "invalid_transition",
                "diagnostic_order_tests",
                "Invalid transition " + from + " -> " + to
        );
    }
}
