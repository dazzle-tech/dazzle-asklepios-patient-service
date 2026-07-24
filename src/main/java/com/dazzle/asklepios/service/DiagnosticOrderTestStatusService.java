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
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.client.WaseelItemMappingClient;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationSubmissionService;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.patientarrived.PatientArrivedCreateRequestDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.PatientArrivedResponseVM;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class DiagnosticOrderTestStatusService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestStatusService.class);

    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderStatusService diagnosticOrderStatusService;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final DiagnosticTestClient diagnosticTestClient;
    private final WaseelItemMappingClient waseelItemMappingClient;
    private final PreAuthorizationSubmissionService preAuthorizationSubmissionService;

    public DiagnosticOrderTestStatusService(
            DiagnosticOrderRepository diagnosticOrderRepository,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            DiagnosticTestClient diagnosticTestClient,
            WaseelItemMappingClient waseelItemMappingClient,
            PreAuthorizationSubmissionService preAuthorizationSubmissionService
    ) {
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.diagnosticTestClient = diagnosticTestClient;
        this.waseelItemMappingClient = waseelItemMappingClient;
        this.preAuthorizationSubmissionService = preAuthorizationSubmissionService;
    }

    public DiagnosticOrderTest collectSample(Long testId) {
        DiagnosticOrderTest test = getTest(testId);
        ensureTransition(test, DiagnosticStatus.SAMPLE_COLLECTED);

        test.setProcessingStatus(DiagnosticStatus.SAMPLE_COLLECTED);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }

    public PatientArrivedResponseVM patientArrived(Long testId, PatientArrivedCreateRequestDTO dto) {
        DiagnosticOrderTest test = getTest(testId);

        if (test.getOrderType() != TestType.RADIOLOGY) {
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

        return new PatientArrivedResponseVM(
                saved.getId(),
                saved.getPatientArrivedDate(),
                saved.getPatientArrivedNoteRad()
        );
    }

    public DiagnosticOrderTest accept(Long testId, String acceptedBy) {
        DiagnosticOrderTest test = getTest(testId);
        ensureTransition(test, DiagnosticStatus.ACCEPTED);

        test.setProcessingStatus(DiagnosticStatus.ACCEPTED);
        test.setAcceptedBy(acceptedBy);
        test.setAcceptedDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.saveAndFlush(test);

        DiagnosticOrder order = getOrder(saved.getOrderId());
        DiagnosticTestSetupDTO setupDiagnostic = fetchDiagnosticTestSetup(saved.getTestId());

        PatientServiceAndProduct billingItem = buildDiagnosticBillingItem(order, saved, setupDiagnostic);

        PatientServiceAndProduct savedBillingItem =
                patientServiceAndProductRepository.saveAndFlush(billingItem);

        if (savedBillingItem.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL) {
            preAuthorizationSubmissionService.submitIfRequired(savedBillingItem.getEncounterId());
        }

        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }

    public DiagnosticOrderTest markReady(Long testId) {
        DiagnosticOrderTest test = getTest(testId);
        ensureTransition(test, DiagnosticStatus.RESULT_READY);

        test.setProcessingStatus(DiagnosticStatus.RESULT_READY);
        test.setReadyDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }

    public DiagnosticOrderTest review(Long testId) {
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }

    public DiagnosticOrderTest approve(Long testId) {
        DiagnosticOrderTest test = getTest(testId);
        ensureTransition(test, DiagnosticStatus.RESULT_APPROVED);

        test.setProcessingStatus(DiagnosticStatus.RESULT_APPROVED);
        test.setApprovedDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }

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

    public DiagnosticOrderTest cancel(Long testId, String cancelledBy, String cancellationReason) {
        DiagnosticOrderTest test = getTest(testId);

        DiagnosticOrderTestStatus current = test.getStatus() == null ? DiagnosticOrderTestStatus.NEW : test.getStatus();

        if (current == DiagnosticOrderTestStatus.CANCELLED) {
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

        return saved;
    }

    public DiagnosticOrderTest undoAccept(Long testId, String username, String undoAcceptReason) {
        DiagnosticOrderTest test = getTest(testId);

        if (test.getProcessingStatus() != DiagnosticStatus.ACCEPTED) {
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

        if (billed) {
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

        patientServiceAndProductRepository
                .deleteByPatientIdAndEncounterIdAndDiagnosticTestIdAndIsBilledFalse(
                        order.getPatientId(),
                        order.getEncounterId(),
                        setupDiagnostic.id()
                );

        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }

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

    private DiagnosticOrderTest getTest(Long testId) {
        return diagnosticOrderTestRepository.findById(testId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + testId
                ));
    }

    private DiagnosticOrder getOrder(Long orderId) {
        return diagnosticOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Diagnostic order not found with id " + orderId,
                        "diagnosticOrder",
                        "notfound"
                ));
    }

    private DiagnosticTestSetupDTO fetchDiagnosticTestSetup(Long diagnosticTestId) {
        try {
            return diagnosticTestClient.getDiagnosticTest(diagnosticTestId);
        } catch (Exception ex) {
            throw new NotFoundAlertException(
                    "Diagnostic test setup not found with id " + diagnosticTestId,
                    "diagnosticTest",
                    "setup.notfound"
            );
        }
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

        boolean requiresPreAuth =
                requiresPreAuthorization(billingItemType, setupDiagnostic.id());

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
                .preAuthorizationStatus(
                        requiresPreAuth
                                ? PreAuthorizationStatus.PENDING_APPROVAL
                                : PreAuthorizationStatus.NOT_REQUIRED
                )
                .isBilled(Boolean.FALSE)
                .notes("Created on diagnostic test acceptance. OrderId="
                        + order.getId() + ", OrderTestId=" + test.getId())
                .build();
    }

    private boolean requiresPreAuthorization(
            BillingItemTypes billingItemType,
            Long itemId
    ) {
        if (billingItemType == null || itemId == null) {
            return false;
        }

        LOG.info(
                "Checking diagnostic pre-authorization from Waseel mapping. billingItemType={}, itemId={}",
                billingItemType,
                itemId
        );

        try {
            Boolean requiresPreAuth =
                    waseelItemMappingClient.requiresPreauth(
                            billingItemType,
                            itemId
                    );

            LOG.info(
                    "Diagnostic pre-authorization result from Waseel mapping. billingItemType={}, itemId={}, result={}",
                    billingItemType,
                    itemId,
                    requiresPreAuth
            );

            return Boolean.TRUE.equals(requiresPreAuth);

        } catch (FeignException ex) {
            LOG.error(
                    "[WASEEL_MAPPING] Failed to check diagnostic pre-authorization. billingItemType={}, itemId={}, status={}, body={}",
                    billingItemType,
                    itemId,
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );

            return false;
        }
    }

    @Transactional(readOnly = true)
    public PatientArrivedResponseVM getPatientArrived(Long testId) {
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

    private void ensureTransition(DiagnosticOrderTest test, DiagnosticStatus to) {
        DiagnosticStatus from = normalize(test.getProcessingStatus());
        TestType type = test.getOrderType();

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
        return new BadRequestAlertException(
                "invalid_transition",
                "diagnostic_order_tests",
                "Invalid transition " + from + " -> " + to
        );
    }
}