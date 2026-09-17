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
import com.dazzle.asklepios.integration.waseel.service.EncounterPreAuthorizationSyncService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.patientarrived.PatientArrivedCreateRequestDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.PatientArrivedResponseVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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
    private final EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService;
    private final PreAuthorizationResolutionService preAuthorizationResolutionService;

    private final PatientEncounterRepository patientEncounterRepository;

    private final BillingEngineService billingEngineService;

    private final BillingChargeService billingChargeService;

    private final PatientServiceAndProductService patientServiceAndProductService;

    private final PatientItemPricingApplicationService patientItemPricingApplicationService;

    private final InsurancePriceListCoverageService insurancePriceListCoverageService;

    public DiagnosticOrderTestStatusService(
            DiagnosticOrderRepository diagnosticOrderRepository,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            DiagnosticOrderStatusService diagnosticOrderStatusService,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            DiagnosticTestClient diagnosticTestClient,
            EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService,
            PreAuthorizationResolutionService preAuthorizationResolutionService,
            PatientEncounterRepository patientEncounterRepository,
            @Lazy BillingEngineService billingEngineService,
            @Lazy BillingChargeService billingChargeService,
            @Lazy PatientServiceAndProductService patientServiceAndProductService,
            PatientItemPricingApplicationService patientItemPricingApplicationService,
            InsurancePriceListCoverageService insurancePriceListCoverageService
    ) {
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderStatusService = diagnosticOrderStatusService;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.diagnosticTestClient = diagnosticTestClient;
        this.encounterPreAuthorizationSyncService = encounterPreAuthorizationSyncService;
        this.preAuthorizationResolutionService = preAuthorizationResolutionService;
        this.patientEncounterRepository = patientEncounterRepository;
        this.billingEngineService = billingEngineService;
        this.billingChargeService = billingChargeService;
        this.patientServiceAndProductService = patientServiceAndProductService;
        this.patientItemPricingApplicationService = patientItemPricingApplicationService;
        this.insurancePriceListCoverageService = insurancePriceListCoverageService;
    }

    public DiagnosticOrderTest collectSample(Long testId) {
        DiagnosticOrderTest test = getTest(testId);
        ensureTransition(test, DiagnosticStatus.SAMPLE_COLLECTED);

        test.setProcessingStatus(DiagnosticStatus.SAMPLE_COLLECTED);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }

    public DiagnosticOrderTest returnToNew(Long testId) {

        LOG.debug("[DiagnosticOrderTestStatus] RETURN_TO_NEW - start. testId={}", testId);

        DiagnosticOrderTest test = getTest(testId);

        test.setProcessingStatus(DiagnosticStatus.NEW);

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.save(test);

        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        LOG.debug(
                "[DiagnosticOrderTestStatus] RETURN_TO_NEW - done. testId={} orderId={} status={}",
                saved.getId(),
                saved.getOrderId(),
                saved.getProcessingStatus()
        );

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

    /**
     * Creates the billing item and runs pre-auth / billing when a test is added to an order.
     * Pre-authorization is submitted here (not on accept).
     */
    public PatientServiceAndProduct onTestAddedToOrder(DiagnosticOrderTest test) {
        return onTestAddedToOrder(test, null);
    }

    public PatientServiceAndProduct onTestAddedToOrder(
            DiagnosticOrderTest test,
            Boolean acceptUncoveredAsCash
    ) {
        if (test == null || test.getId() == null || test.getOrderId() == null) {
            throw new BadRequestAlertException(
                    "Diagnostic order test is required",
                    "diagnostic_order_tests",
                    "test.required"
            );
        }

        DiagnosticOrder order = getOrder(test.getOrderId());
        DiagnosticTestSetupDTO setupDiagnostic = fetchDiagnosticTestSetup(test.getTestId());

        PatientServiceAndProduct savedBillingItem =
                findOrCreateDiagnosticBillingItem(order, test, setupDiagnostic, acceptUncoveredAsCash);

        if (Boolean.TRUE.equals(savedBillingItem.getIsBilled())
                && savedBillingItem.getPreAuthorizationStatus()
                        == PreAuthorizationStatus.PENDING_APPROVAL) {
            throw new BadRequestAlertException(
                    "Diagnostic item requires pre-authorization but was already billed.",
                    "diagnostic_order_tests",
                    "preAuthorization.alreadyBilled"
            );
        }

        if (savedBillingItem.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL) {
            LOG.info(
                    "[DIAGNOSTIC_ORDER] Pre-auth required — submitting to Waseel. encounterId={} pspId={} testId={}",
                    savedBillingItem.getEncounterId(),
                    savedBillingItem.getId(),
                    test.getId()
            );
            try {
                encounterPreAuthorizationSyncService.submitPendingPreAuthorizationOrThrow(
                        savedBillingItem.getEncounterId()
                );
            } catch (BadRequestAlertException ex) {
                String title = ex.getBody() != null && ex.getBody().getTitle() != null
                        ? ex.getBody().getTitle()
                        : ex.getMessage();
                String errorKey = ex.getErrorKey() != null
                        ? ex.getErrorKey()
                        : "preAuthorization.failed";
                throw new BadRequestAlertException(title, "diagnostic_order_tests", errorKey);
            }
        } else {
            billDiagnosticItemOnOrder(
                    savedBillingItem,
                    order.getEncounterId()
            );
        }

        return savedBillingItem;
    }

    public DiagnosticOrderTest accept(Long testId, String acceptedBy) {
        DiagnosticOrderTest test = getTest(testId);
        ensureTransition(test, DiagnosticStatus.ACCEPTED);

        test.setProcessingStatus(DiagnosticStatus.ACCEPTED);
        test.setAcceptedBy(acceptedBy);
        test.setAcceptedDate(Instant.now());

        DiagnosticOrderTest saved = diagnosticOrderTestRepository.saveAndFlush(test);

        // Billing + pre-auth already happen when the test is added to the order.
        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }

    public DiagnosticOrderTest markReady(Long testId) {
        DiagnosticOrderTest test = getTest(testId);
        ensureTransition(test, DiagnosticStatus.EXAM_DONE);

        test.setProcessingStatus(DiagnosticStatus.EXAM_DONE);
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

        DiagnosticStatus processingStatus = normalize(test.getProcessingStatus());

        if (processingStatus != DiagnosticStatus.NEW) {
            throw new BadRequestAlertException(
                    "Cannot cancel because this test has already started",
                    "test_already_started",

                    "diagnostic_order_tests"

            );
        }

        String cancelReason =
                cancellationReason == null || cancellationReason.isBlank()
                        ? "Diagnostic test cancelled"
                        : cancellationReason;

        BillingItemTypes billingItemType =
                test.getOrderType() == TestType.RADIOLOGY
                        ? BillingItemTypes.RADIOLOGY
                        : BillingItemTypes.LABORATORY;

        ServiceSource serviceSource =
                test.getOrderType() == TestType.RADIOLOGY
                        ? ServiceSource.RADIOLOGY
                        : ServiceSource.LABORATORY;

        patientServiceAndProductService.cancelBySource(
                serviceSource,
                test.getId(),
                billingItemType,
                cancelReason
        );

        test.setStatus(DiagnosticOrderTestStatus.CANCELLED);
        test.setProcessingStatus(DiagnosticStatus.CANCELLED);

        test.setCancelledBy(cancelledBy);
        test.setCancelledDate(Instant.now());
        test.setCancellationReason(cancelReason);

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

        // Billing / pre-auth are created when the test is added to the order — do not delete them here.
        LOG.info(
                "[DIAGNOSTIC_UNDO_ACCEPT] testId={} — keeping billing item for diagnosticTestId={}",
                saved.getId(),
                setupDiagnostic.id()
        );

        diagnosticOrderStatusService.recomputeLabRadStatuses(saved.getOrderId());

        return saved;
    }

    public void bulkAccept(List<Long> testIds, String acceptedBy) {
        for (Long id : testIds) {
            accept(id, acceptedBy);
        }
    }
    public void bulkCancel(List<Long> testIds,String cancelBy ,String cancellationReason){
        for (Long id : testIds) {
            cancel(id, cancelBy,cancellationReason);
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
            DiagnosticTestSetupDTO setupDiagnostic,
            Boolean acceptUncoveredAsCash
    ) {
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

        PatientServiceAndProduct.PatientServiceAndProductBuilder builder = PatientServiceAndProduct.builder()
                .patientId(order.getPatientId())
                .encounterId(order.getEncounterId())
                .billingItemType(billingItemType)
                .diagnosticTestId(setupDiagnostic.id())
                .serviceSource(serviceSource)
                .sourceId(test.getId())
                .quantity(quantity)
                .unitPrice(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .exemptionAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .grossAmount(BigDecimal.ZERO)
                .netAmount(BigDecimal.ZERO)
                .patientShareAmount(BigDecimal.ZERO)
                .insuranceShareAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .remainingAmount(BigDecimal.ZERO)
                .currency(setupDiagnostic.currency())
                .isBilled(Boolean.FALSE)
                .notes("Created when diagnostic test was added to order. OrderId="
                        + order.getId() + ", OrderTestId=" + test.getId());

        var coverageCheck = insurancePriceListCoverageService.check(
                order.getEncounterId(),
                billingItemType,
                null,
                null,
                setupDiagnostic.id(),
                null,
                setupDiagnostic.currency()
        );
        insurancePriceListCoverageService.requireCoveredOrAcknowledged(
                coverageCheck,
                acceptUncoveredAsCash
        );

        if (coverageCheck.requiresCashConfirmation()) {
            insurancePriceListCoverageService.applyUncoveredCash(builder, coverageCheck);
        } else {
            preAuthorizationResolutionService.resolveAndPrepareNewItem(
                    builder,
                    order.getEncounterId(),
                    billingItemType,
                    null,
                    null,
                    setupDiagnostic.id(),
                    null,
                    true,
                    setupDiagnostic.currency()
            );

            preAuthorizationResolutionService.enrichWaseelSbsMappingForBillingItem(
                    builder,
                    billingItemType,
                    null,
                    null,
                    setupDiagnostic.id(),
                    null
            );
        }

        PatientServiceAndProduct billingItem = builder.build();
        applyResolvedDiagnosticPricing(billingItem, order.getEncounterId(), quantity);

        return billingItem;
    }

    private void applyResolvedDiagnosticPricing(
            PatientServiceAndProduct item,
            Long encounterId,
            long quantity
    ) {
        Long facilityId = patientEncounterRepository
                .findById(encounterId)
                .map(encounter -> encounter.getFacilityId())
                .orElse(null);

        patientItemPricingApplicationService.applyToItem(item, facilityId);
    }

    private PatientServiceAndProduct findOrCreateDiagnosticBillingItem(
            DiagnosticOrder order,
            DiagnosticOrderTest test,
            DiagnosticTestSetupDTO setupDiagnostic,
            Boolean acceptUncoveredAsCash
    ) {
        BillingItemTypes billingItemType =
                test.getOrderType() == TestType.RADIOLOGY
                        ? BillingItemTypes.RADIOLOGY
                        : BillingItemTypes.LABORATORY;

        ServiceSource serviceSource =
                test.getOrderType() == TestType.RADIOLOGY
                        ? ServiceSource.RADIOLOGY
                        : ServiceSource.LABORATORY;

        return patientServiceAndProductRepository
                .findByServiceSourceAndSourceIdAndBillingItemType(
                        serviceSource,
                        test.getId(),
                        billingItemType
                )
                .map(existing ->
                        refreshExistingDiagnosticBillingItem(
                                existing,
                                order.getEncounterId()
                        )
                )
                .orElseGet(() ->
                        patientServiceAndProductRepository.saveAndFlush(
                                buildDiagnosticBillingItem(
                                        order,
                                        test,
                                        setupDiagnostic,
                                        acceptUncoveredAsCash
                                )
                        )
                );
    }

    private PatientServiceAndProduct refreshExistingDiagnosticBillingItem(
            PatientServiceAndProduct existing,
            Long encounterId
    ) {
        if (existing.isUncoveredCashItem()) {
            return existing;
        }

        preAuthorizationResolutionService.applyEncounterInsuranceLink(
                existing,
                encounterId,
                true
        );
        preAuthorizationResolutionService.refreshPreAuthorization(
                existing,
                true
        );

        return patientServiceAndProductRepository.saveAndFlush(existing);
    }

    private void billDiagnosticItemOnOrder(
            PatientServiceAndProduct billingItem,
            Long encounterId
    ) {
        if (billingItem == null || billingItem.getId() == null) {
            return;
        }

        Long facilityId =
                patientEncounterRepository
                        .findById(encounterId)
                        .map(encounter -> encounter.getFacilityId())
                        .orElse(null);

        if (facilityId == null) {
            throw new BadRequestAlertException(
                    "Encounter facility is required to bill diagnostic item.",
                    "diagnostic_order_tests",
                    "encounter.facility.required"
            );
        }

        if (billingChargeService
                .findActiveChargeLine(
                        billingItem.getId(),
                        encounterId
                )
                .isPresent()) {
            LOG.info(
                    "[DIAG_ORDER_BILLING] Charge line already exists pspId={} encounterId={}",
                    billingItem.getId(),
                    encounterId
            );
            return;
        }

        BillingOperationResult result =
                billingEngineService.onItemOrdered(
                        billingItem.getId(),
                        facilityId,
                        "DIAG-ORDER:" + billingItem.getId()
                );

        LOG.info(
                "[DIAG_ORDER_BILLING] pspId={} processed={} chargeLineId={} message={}",
                billingItem.getId(),
                result.processed(),
                result.chargeLineId(),
                result.message()
        );

        if (!result.processed()) {
            throw new BadRequestAlertException(
                    result.message() == null
                            ? "Diagnostic billing rule did not match when adding the test."
                            : result.message(),
                    "diagnostic_order_tests",
                    "billingRule.notMatched"
            );
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
            if (!(from == DiagnosticStatus.RESULT_READY
                    || from == DiagnosticStatus.EXAM_DONE
                    || from == DiagnosticStatus.PARTIALLY)) {
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