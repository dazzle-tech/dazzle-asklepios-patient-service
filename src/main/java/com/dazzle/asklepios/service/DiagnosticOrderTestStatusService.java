package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.DiagnosticTestClient;
import com.dazzle.asklepios.client.setup.dto.DiagnosticTestSetupDTO;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.patientarrived.PatientArrivedCreateRequestDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.PatientArrivedResponseVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final DiagnosticTestClient diagnosticTestClient;

    public DiagnosticOrderTestStatusService(DiagnosticOrderRepository diagnosticOrderRepository, DiagnosticOrderTestRepository diagnosticOrderTestRepository, DiagnosticOrderStatusService diagnosticOrderStatusService, PatientServiceAndProductRepository patientServiceAndProductRepository, DiagnosticTestClient diagnosticTestClient) {
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.diagnosticTestClient = diagnosticTestClient;
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

        LOG.debug("[DiagnosticOrderTestStatus] COLLECT_SAMPLE - done. testId={} orderId={} status={}", saved.getId(), saved.getOrderId(), saved.getProcessingStatus());

        return saved;
    }

    /**
     * Radiology-only: patient arrival. Sets processingStatus to PATIENT_ARRIVED and stores arrival metadata.
     */
    public PatientArrivedResponseVM patientArrived(Long testId, PatientArrivedCreateRequestDTO dto) {
        LOG.debug("[DiagnosticOrderTestStatus] PATIENT_ARRIVED - start. testId={} payload={}", testId, dto);

        DiagnosticOrderTest test = getTest(testId);

        if (test.getOrderType() != TestType.RADIOLOGY) {
            LOG.warn("[DiagnosticOrderTestStatus] PATIENT_ARRIVED - rejected. testId={} reason=not_radiology", testId);
            throw new BadRequestAlertException(
                    "not_radiology",
                    "diagnostic_order_tests",
                    "Test is not radiology"
            );
        }

        ensureTransition(test, DiagnosticStatus.PATIENT_ARRIVED);

        test.setPatientArrivedDate(dto.patientArrivedDate() != null ? dto.patientArrivedDate() : Instant.now());
        test.setPatientArrivedNoteRad(dto.patientArrivedNoteRad());
        test.setProcessingStatus(DiagnosticStatus.PATIENT_ARRIVED);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        LOG.debug("[DiagnosticOrderTestStatus] PATIENT_ARRIVED - done. testId={} orderId={} status={} arrivedDate={}", saved.getId(), saved.getOrderId(), saved.getProcessingStatus(), saved.getPatientArrivedDate());

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

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.saveAndFlush(test);

        LOG.debug(
                "[DiagnosticOrderTestStatus] ACCEPT - test status persisted. testId={} orderId={} status={}",
                saved.getId(),
                saved.getOrderId(),
                saved.getProcessingStatus()
        );

        DiagnosticOrder order = getOrder(saved.getOrderId());
        DiagnosticTestSetupDTO setupDiagnostic = fetchDiagnosticTestSetup(saved.getTestId());

        PatientServiceAndProduct billingItem = buildDiagnosticBillingItem(order, saved, setupDiagnostic);
        patientServiceAndProductRepository.saveAndFlush(billingItem);

        LOG.info(
                "[DiagnosticOrderTestStatus] ACCEPT - billing created. testId={} orderId={} diagnosticTestId={} sourceId={} billingType={} source={} unitPrice={} totalAmount={}",
                saved.getId(),
                saved.getOrderId(),
                setupDiagnostic.id(),
                billingItem.getSourceId(),
                billingItem.getBillingItemType(),
                billingItem.getServiceSource(),
                billingItem.getUnitPrice(),
                billingItem.getTotalAmount()
        );

        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        LOG.debug(
                "[DiagnosticOrderTestStatus] ACCEPT - done. testId={} orderId={} status={} acceptedBy={}",
                saved.getId(),
                saved.getOrderId(),
                saved.getProcessingStatus(),
                saved.getAcceptedBy()
        );

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

        LOG.debug(
                "[DiagnosticOrderTestStatus] MARK_READY - done. testId={} orderId={} status={} readyDate={}",
                saved.getId(),
                saved.getOrderId(),
                saved.getProcessingStatus(),
                saved.getReadyDate()
        );

        return saved;
    }

    public DiagnosticOrderTest review(Long testId) {
        LOG.debug("[DiagnosticOrderTestStatus] REVIEW - start. testId={}", testId);

        DiagnosticOrderTest test = getTest(testId);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        LOG.debug(
                "[DiagnosticOrderTestStatus] REVIEW - done. testId={} orderId={} status={}",
                saved.getId(),
                saved.getOrderId(),
                saved.getProcessingStatus()
        );

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

        LOG.debug(
                "[DiagnosticOrderTestStatus] APPROVE - done. testId={} orderId={} status={} approvedDate={}",
                saved.getId(),
                saved.getOrderId(),
                saved.getProcessingStatus(),
                saved.getApprovedDate()
        );

        return saved;
    }

    public DiagnosticOrderTest reject(Long testId, String rejectedBy, String rejectedReason) {
        LOG.debug(
                "[DiagnosticOrderTestStatus] REJECT - start. testId={} rejectedBy={} reason={}",
                testId,
                rejectedBy,
                rejectedReason
        );

        DiagnosticOrderTest test = getTest(testId);
        ensureTransition(test, DiagnosticStatus.REJECTED);

        test.setProcessingStatus(DiagnosticStatus.REJECTED);
        test.setRejectedBy(rejectedBy);
        test.setRejectedReason(rejectedReason);
        test.setRejectedDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        LOG.debug(
                "[DiagnosticOrderTestStatus] REJECT - done. testId={} orderId={} status={} rejectedBy={}",
                saved.getId(),
                saved.getOrderId(),
                saved.getProcessingStatus(),
                saved.getRejectedBy()
        );

        return saved;
    }

    public DiagnosticOrderTest cancel(Long testId, String cancelledBy, String cancellationReason) {
        LOG.debug(
                "[DiagnosticOrderTestStatus] CANCEL - start. testId={} cancelledBy={} reason={}",
                testId,
                cancelledBy,
                cancellationReason
        );

        DiagnosticOrderTest test = getTest(testId);

        DiagnosticOrderTestStatus current = test.getStatus() == null ? DiagnosticOrderTestStatus.NEW : test.getStatus();
        if (current == DiagnosticOrderTestStatus.CANCELLED) {
            LOG.warn("[DiagnosticOrderTestStatus] CANCEL - rejected. testId={} reason=already_cancelled", testId);
            throw new BadRequestAlertException(
                    "Already cancelled",
                    "diagnostic_order_tests",
                    "already_cancelled"
            );
        }

        test.setStatus(DiagnosticOrderTestStatus.CANCELLED);
        test.setCancelledBy(cancelledBy);
        test.setCancelledDate(Instant.now());
        test.setCancellationReason(cancellationReason);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        LOG.debug(
                "[DiagnosticOrderTestStatus] CANCEL - done. testId={} orderId={} status={} cancelledBy={}",
                saved.getId(),
                saved.getOrderId(),
                saved.getStatus(),
                saved.getCancelledBy()
        );

        return saved;
    }

    // ---------------------------------------------------------------------
    // Undo Accept
    // ---------------------------------------------------------------------

    public DiagnosticOrderTest undoAccept(Long testId, String username, String undoAcceptReason) {
        LOG.debug("[DiagnosticOrderTestStatus] UNDO_ACCEPT - start. testId={}", testId);

        DiagnosticOrderTest test = getTest(testId);

        if (test.getProcessingStatus() != DiagnosticStatus.ACCEPTED) {
            LOG.warn(
                    "[DiagnosticOrderTestStatus] UNDO_ACCEPT - rejected. testId={} currentStatus={}",
                    testId,
                    test.getProcessingStatus()
            );
            throw new BadRequestAlertException(
                    "invalid_transition",
                    "diagnostic_order_tests",
                    "Undo accept is allowed only from ACCEPTED, current=" + test.getProcessingStatus()
            );
        }

        DiagnosticOrder order = getOrder(test.getOrderId());
        DiagnosticTestSetupDTO setupDiagnostic = fetchDiagnosticTestSetup(test.getTestId());

        boolean billed = patientServiceAndProductRepository
                .existsByPatientIdAndEncounterIdAndDiagnosticTestIdAndIsBilledTrue(
                        order.getPatientId(),
                        order.getEncounterId(),
                        setupDiagnostic.id()
                );

        LOG.debug(
                "[DiagnosticOrderTestStatus] UNDO_ACCEPT - billing check. testId={} patientId={} encounterId={} diagnosticTestId={} billed={}",
                test.getId(),
                order.getPatientId(),
                order.getEncounterId(),
                setupDiagnostic.id(),
                billed
        );

        if (billed) {
            LOG.warn(
                    "[DiagnosticOrderTestStatus] UNDO_ACCEPT - blocked because billed. testId={} orderId={} diagnosticTestId={}",
                    test.getId(),
                    test.getOrderId(),
                    setupDiagnostic.id()
            );
            throw new BadRequestAlertException(
                    "billed_item_cannot_undo_accept",
                    "diagnostic_order_tests",
                    "Cannot undo accept because this diagnostic test is already billed"
            );
        }

        if (test.getOrderType() == TestType.RADIOLOGY) {
            test.setProcessingStatus(DiagnosticStatus.PATIENT_ARRIVED);
        } else {
            test.setProcessingStatus(DiagnosticStatus.SAMPLE_COLLECTED);
        }

        test.setAcceptedBy(null);
        test.setAcceptedDate(null);
        test.setUndoAcceptReason(undoAcceptReason);
        test.setUndoAcceptBy(username);
        test.setUndoAcceptDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.saveAndFlush(test);

        int deletedCount = patientServiceAndProductRepository
                .deleteByPatientIdAndEncounterIdAndDiagnosticTestIdAndIsBilledFalse(
                        order.getPatientId(),
                        order.getEncounterId(),
                        setupDiagnostic.id()
                );

        LOG.info(
                "[DiagnosticOrderTestStatus] UNDO_ACCEPT - deleted unbilled billing items. testId={} orderId={} diagnosticTestId={} deletedCount={}",
                saved.getId(),
                saved.getOrderId(),
                setupDiagnostic.id(),
                deletedCount
        );

        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        LOG.info(
                "[DiagnosticOrderTestStatus] UNDO_ACCEPT - done. testId={} orderId={} revertedStatus={}",
                saved.getId(),
                saved.getOrderId(),
                saved.getProcessingStatus()
        );

        return saved;
    }

    // ---------------------------------------------------------------------
    // Bulk actions
    // ---------------------------------------------------------------------

    public void bulkAccept(List<Long> testIds, String acceptedBy) {
        LOG.debug("[DiagnosticOrderTestStatus] BULK_ACCEPT - start. testIds={} acceptedBy={}", testIds, acceptedBy);

        Set<Long> orderIds = new HashSet<>();

        for (Long id : testIds) {
            DiagnosticOrderTest test = getTest(id);

            ensureTransition(test, DiagnosticStatus.ACCEPTED);

            test.setProcessingStatus(DiagnosticStatus.ACCEPTED);
            test.setAcceptedBy(acceptedBy);
            test.setAcceptedDate(Instant.now());

            DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
            orderIds.add(saved.getOrderId());

            LOG.debug(
                    "[DiagnosticOrderTestStatus] BULK_ACCEPT - item done. testId={} orderId={} status={}",
                    saved.getId(),
                    saved.getOrderId(),
                    saved.getProcessingStatus()
            );
        }

        for (Long orderId : orderIds) {
            diagnosticOrderStatusService.recomputeLabRadStatuses(orderId);
        }

        LOG.debug("[DiagnosticOrderTestStatus] BULK_ACCEPT - done. affectedOrders={}", orderIds);
    }

    public void bulkReject(List<Long> testIds, String rejectedBy, String rejectedReason) {
        LOG.debug(
                "[DiagnosticOrderTestStatus] BULK_REJECT - start. testIds={} rejectedBy={} reason={}",
                testIds,
                rejectedBy,
                rejectedReason
        );

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

            LOG.debug(
                    "[DiagnosticOrderTestStatus] BULK_REJECT - item done. testId={} orderId={} status={}",
                    saved.getId(),
                    saved.getOrderId(),
                    saved.getProcessingStatus()
            );
        }

        for (Long orderId : orderIds) {
            diagnosticOrderStatusService.recomputeLabRadStatuses(orderId);
        }

        LOG.debug("[DiagnosticOrderTestStatus] BULK_REJECT - done. affectedOrders={}", orderIds);
    }

    // ---------------------------------------------------------------------
    // Reads / Helpers
    // ---------------------------------------------------------------------

    private DiagnosticOrderTest getTest(Long testId) {
        LOG.debug("[DiagnosticOrderTestStatus] GET_TEST - testId={}", testId);

        return diagnosticOrderTestRepository.findById(testId)
                .orElseThrow(() -> {
                    LOG.warn("[DiagnosticOrderTestStatus] GET_TEST - not found. testId={}", testId);
                    return new BadRequestAlertException(
                            "notfound",
                            "diagnostic_order_tests",
                            "DiagnosticOrderTest not found with id " + testId
                    );
                });
    }

    private DiagnosticOrder getOrder(Long orderId) {
        LOG.debug("[DiagnosticOrderTestStatus] GET_ORDER - orderId={}", orderId);

        return diagnosticOrderRepository.findById(orderId)
                .orElseThrow(() -> {
                    LOG.warn("[DiagnosticOrderTestStatus] GET_ORDER - not found. orderId={}", orderId);
                    return new NotFoundAlertException(
                            "Diagnostic order not found with id " + orderId,
                            "diagnosticOrder",
                            "notfound"
                    );
                });
    }

    private DiagnosticTestSetupDTO fetchDiagnosticTestSetup(Long diagnosticTestId) {
        LOG.debug("[DiagnosticOrderTestStatus] FETCH_SETUP - diagnosticTestId={}", diagnosticTestId);

        try {
            DiagnosticTestSetupDTO dto = diagnosticTestClient.getDiagnosticTest(diagnosticTestId);

            LOG.debug(
                    "[DiagnosticOrderTestStatus] FETCH_SETUP - success. diagnosticTestId={} name={} price={} currency={}",
                    diagnosticTestId,
                    dto.name(),
                    dto.price(),
                    dto.currency()
            );

            return dto;
        } catch (Exception ex) {
            LOG.warn("[DiagnosticOrderTestStatus] FETCH_SETUP - failed. diagnosticTestId={}", diagnosticTestId, ex);
            throw new NotFoundAlertException(
                    "Diagnostic test setup not found with id " + diagnosticTestId,
                    "diagnosticTest",
                    "setup.notfound"
            );
        }
    }

    private boolean isDiagnosticTestBilled(Long patientId, Long encounterId, Long diagnosticTestId) {
        boolean billed = patientServiceAndProductRepository
                .existsByPatientIdAndEncounterIdAndDiagnosticTestIdAndIsBilledTrue(
                        patientId,
                        encounterId,
                        diagnosticTestId
                );

        LOG.debug(
                "[DiagnosticOrderTestStatus] CHECK_BILLED - patientId={} encounterId={} diagnosticTestId={} billed={}",
                patientId,
                encounterId,
                diagnosticTestId,
                billed
        );

        return billed;
    }

    private PatientServiceAndProduct buildDiagnosticBillingItem(
            DiagnosticOrder order,
            DiagnosticOrderTest test,
            DiagnosticTestSetupDTO setupDiagnostic
    ) {
        BigDecimal unitPrice = setupDiagnostic.price() != null ? setupDiagnostic.price() : BigDecimal.ZERO;
        long quantity = 1L;

        BillingItemTypes billingItemType;
        ServiceSource serviceSource;

        if (test.getOrderType() == TestType.RADIOLOGY) {
            billingItemType = BillingItemTypes.RADIOLOGY;
            serviceSource = ServiceSource.RADIOLOGY;
        } else {
            billingItemType = BillingItemTypes.LABORATORY;
            serviceSource = ServiceSource.LABORATORY;
        }

        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));

        LOG.debug(
                "[DiagnosticOrderTestStatus] BUILD_BILLING - orderId={} testId={} diagnosticTestId={} sourceId={} orderType={} billingType={} serviceSource={} unitPrice={} quantity={} totalAmount={}",
                order.getId(),
                test.getId(),
                setupDiagnostic.id(),
                test.getId(),
                test.getOrderType(),
                billingItemType,
                serviceSource,
                unitPrice,
                quantity,
                totalAmount
        );

        return PatientServiceAndProduct.builder()
                .patientId(order.getPatientId())
                .encounterId(order.getEncounterId())
                .billingItemType(billingItemType)
                .diagnosticTestId(setupDiagnostic.id())
                .serviceSource(serviceSource)
                .sourceId(test.getId())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .discountAmount(BigDecimal.ZERO)
                .exemptionAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .currency(setupDiagnostic.currency())
                .isBilled(Boolean.FALSE)
                .notes("Created on diagnostic test acceptance. OrderId="
                        + order.getId() + ", OrderTestId=" + test.getId())
                .build();
    }

    @Transactional(readOnly = true)
    public PatientArrivedResponseVM getPatientArrived(Long testId) {
        LOG.debug("[DiagnosticOrderTestStatus] GET_PATIENT_ARRIVED - testId={}", testId);

        DiagnosticOrderTest test = diagnosticOrderTestRepository.findById(testId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + testId
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
            if (type == TestType.RADIOLOGY) {
                throw invalid(from, to);
            }
            if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.SAMPLE_COLLECTED)) {
                throw invalid(from, to);
            }
            return;
        }

        if (to == DiagnosticStatus.PATIENT_ARRIVED) {
            if (type != TestType.RADIOLOGY) {
                throw invalid(from, to);
            }
            if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.PATIENT_ARRIVED)) {
                throw invalid(from, to);
            }
            return;
        }

        if (to == DiagnosticStatus.ACCEPTED) {
            if (type == TestType.RADIOLOGY) {
                if (from != DiagnosticStatus.PATIENT_ARRIVED) {
                    throw invalid(from, to);
                }
                if (test.getPatientArrivedDate() == null) {
                    throw invalid(from, to);
                }
                return;
            }

            if (from != DiagnosticStatus.SAMPLE_COLLECTED) {
                throw invalid(from, to);
            }
            return;
        }

        if (to == DiagnosticStatus.RESULT_READY) {
            if (from != DiagnosticStatus.ACCEPTED) {
                throw invalid(from, to);
            }
            return;
        }

        if (to == DiagnosticStatus.RESULT_APPROVED) {
            if (!(from == DiagnosticStatus.RESULT_READY || from == DiagnosticStatus.PARTIALLY)) {
                throw invalid(from, to);
            }
            return;
        }

        if (to == DiagnosticStatus.REJECTED) {
            if (type == TestType.RADIOLOGY) {
                if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.PATIENT_ARRIVED)) {
                    throw invalid(from, to);
                }
                return;
            }

            if (!(from == DiagnosticStatus.NEW || from == DiagnosticStatus.SAMPLE_COLLECTED)) {
                throw invalid(from, to);
            }
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