package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.domain.enumeration.PriceSource;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCancellationReason;
import com.dazzle.asklepios.domain.enumeration.billing.BillingEventType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.CancelReason;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.EncounterInsuranceEligibilityService;
import com.dazzle.asklepios.integration.waseel.service.EncounterPreAuthorizationSyncService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationCancellationService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.BillingCancellationRequest;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.service.dto.billing.BillingRuleEvaluationRequest;
import com.dazzle.asklepios.service.dto.billing.ResolvedBillingPrice;
import com.dazzle.asklepios.service.dto.patientServiceProduct.PatientServiceProductCreateDTO;
import com.dazzle.asklepios.service.dto.patientServiceProduct.PatientServiceProductUpdateDTO;
import com.dazzle.asklepios.service.helper.BrandMedicationHelper;
import com.dazzle.asklepios.service.helper.DiagnosticTestHelper;
import com.dazzle.asklepios.service.helper.ProcedureHelper;
import com.dazzle.asklepios.service.helper.ServiceHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class PatientServiceAndProductService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientServiceAndProductService.class);

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final DiagnosticTestHelper diagnosticTestHelper;
    private final ServiceHelper serviceHelper;
    private final ProcedureHelper procedureHelper;
    private final BrandMedicationHelper brandMedicationHelper;
    private final PreAuthorizationResolutionService preAuthorizationResolutionService;
    private final EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService;
    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;
    private final PreAuthorizationCancellationService preAuthorizationCancellationService;
    private final BillingRuleEvaluationService billingRuleEvaluationService;
    private final BillingEngineService billingEngineService;
    private final PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository;

    public PatientServiceAndProductService(
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            PatientRepository patientRepository,
            PatientEncounterRepository patientEncounterRepository,
            DiagnosticTestHelper diagnosticTestHelper,
            ServiceHelper serviceHelper,
            ProcedureHelper procedureHelper,
            BrandMedicationHelper brandMedicationHelper,
            PreAuthorizationResolutionService preAuthorizationResolutionService,
            EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService,
            EncounterInsuranceEligibilityService encounterInsuranceEligibilityService,
            PreAuthorizationCancellationService preAuthorizationCancellationService,
            BillingRuleEvaluationService billingRuleEvaluationService,
            @Lazy BillingEngineService billingEngineService,
            PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository
    ) {
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.diagnosticTestHelper = diagnosticTestHelper;
        this.serviceHelper = serviceHelper;
        this.procedureHelper = procedureHelper;
        this.brandMedicationHelper = brandMedicationHelper;
        this.preAuthorizationResolutionService = preAuthorizationResolutionService;
        this.encounterPreAuthorizationSyncService = encounterPreAuthorizationSyncService;
        this.encounterInsuranceEligibilityService = encounterInsuranceEligibilityService;
        this.preAuthorizationCancellationService = preAuthorizationCancellationService;
        this.billingRuleEvaluationService = billingRuleEvaluationService;
        this.billingEngineService = billingEngineService;
        this.patientPrescriptionMedicationRepository = patientPrescriptionMedicationRepository;
    }

    public PatientServiceAndProduct create(PatientServiceProductCreateDTO dto) {
        LOG.debug("Request to create Patient billing item : {}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "procedure",
                        "patient.notfound"
                ));

        PatientEncounter encounter = patientEncounterRepository.findById(dto.encounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + dto.encounterId(),
                        "procedure",
                        "encounter.notfound"
                ));

        validateReferences(dto);
        validateNoDuplicateEncounterItem(dto, encounter.getId());
        billingRuleEvaluationService.requireConfiguredRule(
                dto,
                resolveBillingEvent(dto)
        );

        PatientServiceAndProduct entity = buildEntityFromCreateDto(
                dto,
                patient.getId(),
                encounter
        );

        try {
            PatientServiceAndProduct saved = patientServiceAndProductRepository.saveAndFlush(entity);

            completeItemBillingFlow(saved);

            LOG.debug("Created Patient billing item : {}", saved);
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<PatientServiceAndProduct> findAllServicesAndProductsByEncounterId(Pageable pageable, Long encounterId) {
        LOG.debug("Fetch Patient billing items for encounter : {}", encounterId);
        return patientServiceAndProductRepository.findAllByEncounterId(encounterId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PatientServiceAndProduct> findAllServicesAndProductsByPatientId(Pageable pageable, Long patientId) {
        LOG.debug("Fetch Patient Services & Products for patient : {}", patientId);
        return patientServiceAndProductRepository.findAllByPatientId(patientId, pageable);
    }

    @Transactional
    public PatientServiceAndProduct update(PatientServiceProductUpdateDTO dto) {
        LOG.debug("Request to update Patient billing item : {}", dto);

        PatientServiceAndProduct entity = patientServiceAndProductRepository.findById(dto.id())
                .orElseThrow(() -> new BadRequestAlertException(
                        "idNotFound",
                        "patientServicesAndProducts",
                        "Record not found with id " + dto.id()
                ));

        PatientEncounter encounter = patientEncounterRepository.findById(entity.getEncounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + entity.getEncounterId(),
                        "patientServicesAndProducts",
                        "encounter.notfound"
                ));

        entity.setBillingItemType(dto.billingItemType());

        if (dto.brandMedicationId() != null) {
            brandMedicationHelper.validateBrandMedicationExists(dto.brandMedicationId());
            entity.setBrandMedicationId(dto.brandMedicationId());
        }

        if (dto.diagnosticTestId() != null) {
            diagnosticTestHelper.getDiagnosticTest(dto.diagnosticTestId());
            entity.setDiagnosticTestId(dto.diagnosticTestId());
        }

        if (dto.serviceId() != null) {
            serviceHelper.validateServiceExists(dto.serviceId());
            entity.setServiceId(dto.serviceId());
        }

        if (dto.procedureId() != null) {
            procedureHelper.validateProcedureExists(dto.procedureId());
            entity.setProcedureId(dto.procedureId());
        }

        entity.setQuantity(dto.quantity());
        entity.setCurrency(dto.currency());
        entity.setDiscountAmount(defaultZero(dto.discountAmount()));
        entity.setExemptionAmount(defaultZero(dto.exemptionAmount()));
        entity.setTaxAmount(defaultZero(dto.taxAmount()));

        preAuthorizationResolutionService.apply(
                entity,
                preAuthorizationResolutionService.resolve(
                        entity.getEncounterId(),
                        dto.billingItemType(),
                        dto.procedureId(),
                        dto.serviceId(),
                        dto.diagnosticTestId(),
                        dto.brandMedicationId()
                )
        );

        if (encounterInsuranceEligibilityService.shouldEvaluatePreAuthorization(entity.getEncounterId())) {
            entity.setPatientInsuranceId(
                    encounterInsuranceEligibilityService.resolveEncounterPatientInsuranceId(
                            entity.getEncounterId()
                    )
            );
            entity.setCoverageStatus(CoverageStatus.COVERED);
        }

        applyResolvedPricing(entity, encounter, dto.quantity());

        if (dto.isBilled() != null) {
            entity.setIsBilled(dto.isBilled());
        }

        entity.setBillingInvoiceId(dto.billingInvoiceId());
        entity.setBillingInvoiceItemId(dto.billingInvoiceItemId());

        try {
            PatientServiceAndProduct updated = patientServiceAndProductRepository.saveAndFlush(entity);

            completeItemBillingFlow(updated);

            LOG.debug("Updated Patient billing item : {}", updated);
            return updated;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    @Transactional
    public List<PatientServiceAndProduct> createBulk(List<PatientServiceProductCreateDTO> dtos) {
        LOG.debug("Request to bulk create Patient billing items : count={}", dtos == null ? 0 : dtos.size());

        if (dtos == null || dtos.isEmpty()) {
            throw new BadRequestAlertException(
                    "emptyRequest",
                    "patient_services_and_products",
                    "Billing items list cannot be empty"
            );
        }

        try {
            List<PatientServiceAndProduct> entities = dtos.stream()
                    .map(dto -> {
                        Patient patient = patientRepository.findById(dto.patientId())
                                .orElseThrow(() -> new NotFoundAlertException(
                                        "Patient not found with id " + dto.patientId(),
                                        "procedure",
                                        "patient.notfound"
                                ));

                        PatientEncounter encounter = patientEncounterRepository.findById(dto.encounterId())
                                .orElseThrow(() -> new NotFoundAlertException(
                                        "Encounter not found with id " + dto.encounterId(),
                                        "procedure",
                                        "encounter.notfound"
                                ));

                        validateReferences(dto);
                        validateNoDuplicateEncounterItem(dto, encounter.getId());

                        return buildEntityFromCreateDto(
                                dto,
                                patient.getId(),
                                encounter
                        );
                    })
                    .toList();

            List<PatientServiceAndProduct> saved = patientServiceAndProductRepository.saveAll(entities);
            patientServiceAndProductRepository.flush();

            saved.stream()
                    .map(PatientServiceAndProduct::getEncounterId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(encounterId -> {
                        boolean hasPending = saved.stream()
                                .anyMatch(item ->
                                        Objects.equals(item.getEncounterId(), encounterId)
                                                && item.getPreAuthorizationStatus()
                                                == PreAuthorizationStatus.PENDING_APPROVAL
                                );
                        if (hasPending) {
                            submitPreAuthorizationOrThrow(encounterId);
                        }
                    });

            for (PatientServiceAndProduct item : saved) {
                if (item.getPreAuthorizationStatus() != PreAuthorizationStatus.PENDING_APPROVAL) {
                    billItemNow(item);
                }
            }

            LOG.debug("Bulk created Patient billing items : count={}", saved.size());
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    /**
     * When a prescription is submitted on an insurance visit, create billing rows for each
     * medication and run the same Waseel pre-authorization flow used for services/procedures.
     */
    @Transactional
    public void syncPrescriptionMedicationsForPreAuthorization(PatientPrescription prescription) {
        if (prescription == null
                || prescription.getId() == null
                || prescription.getEncounterId() == null
                || prescription.getPatient() == null) {
            return;
        }

        if (!encounterInsuranceEligibilityService.shouldEvaluatePreAuthorization(
                prescription.getEncounterId()
        )) {
            return;
        }

        PatientEncounter encounter = patientEncounterRepository.findById(prescription.getEncounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + prescription.getEncounterId(),
                        "patientServicesAndProducts",
                        "encounter.notfound"
                ));

        List<PatientPrescriptionMedication> medications =
                patientPrescriptionMedicationRepository.findByPrescriptionHeader_Id(prescription.getId());

        if (medications == null || medications.isEmpty()) {
            return;
        }

        List<PatientServiceAndProduct> createdItems = new ArrayList<>();

        for (PatientPrescriptionMedication medication : medications) {
            if (medication == null
                    || PrescriptionStatus.CANCELLED.equals(medication.getStatus())
                    || medication.getMedicationsId() == null) {
                continue;
            }

            if (patientServiceAndProductRepository
                    .findByServiceSourceAndSourceIdAndBillingItemType(
                            ServiceSource.PRESCRIPTION,
                            medication.getId(),
                            BillingItemTypes.MEDICATION
                    ).isPresent()) {
                continue;
            }

            if (patientServiceAndProductRepository.existsByEncounterIdAndBillingItemTypeAndBrandMedicationId(
                    prescription.getEncounterId(),
                    BillingItemTypes.MEDICATION,
                    medication.getMedicationsId()
            )) {
                continue;
            }

            billingRuleEvaluationService.requireConfiguredRule(
                    new BillingRuleEvaluationRequest(
                            BillingItemTypes.MEDICATION,
                            BillingEventType.ITEM_ORDERED,
                            null,
                            null,
                            null,
                            medication.getMedicationsId()
                    )
            );

            PatientServiceAndProduct entity =
                    buildMedicationEntityFromPrescription(
                            prescription,
                            encounter,
                            medication
                    );

            createdItems.add(
                    patientServiceAndProductRepository.saveAndFlush(entity)
            );
        }

        if (createdItems.isEmpty()) {
            return;
        }

        boolean hasPendingPreAuthorization = createdItems.stream()
                .anyMatch(item ->
                        item.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL
                );

        if (hasPendingPreAuthorization) {
            submitPreAuthorizationOrThrow(prescription.getEncounterId());
        }

        for (PatientServiceAndProduct item : createdItems) {
            if (item.getPreAuthorizationStatus() != PreAuthorizationStatus.PENDING_APPROVAL) {
                billItemNow(item);
            }
        }
    }

    @Transactional
    public void remove(Long id) {
        cancel(id, "Service/product item cancelled");
    }

    /**
     * Cancels a patient service/product item financially and, when insurance pre-auth exists, via Waseel cancel API.
     * Soft-cancels the billing row (does not hard-delete).
     */
    @Transactional
    public void cancel(Long id, String reason) {
        LOG.debug("Request to cancel Patient billing item : id={} reason={}", id, reason);

        PatientServiceAndProduct entity = patientServiceAndProductRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Patient service/product not found with id " + id,
                        "patientServicesAndProducts",
                        "idNotFound"
                ));

        if (entity.getPaymentStatus() == PaymentStatus.CANCELLED) {
            LOG.debug("Patient billing item already cancelled : id={}", id);
            return;
        }

        String cancelReasonText =
                reason == null || reason.isBlank()
                        ? "Service/product item cancelled"
                        : reason;

        try {
            preAuthorizationCancellationService.cancelForItem(
                    entity,
                    CancelReason.SERVICE_NOT_PERFORMED
            );

            String cancelledBy = SecurityUtils.getCurrentUserLogin().orElse("system");
            String requestId = "psp-cancel-" + id + "-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID();

            billingEngineService.cancelPatientService(
                    new BillingCancellationRequest(
                            id,
                            BillingCancellationReason.SERVICE_CANCELLED,
                            cancelReasonText,
                            cancelledBy,
                            requestId,
                            BillingLedgerSourceChannel.API
                    )
            );
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }

        LOG.debug("Cancelled Patient billing item : id={}", id);
    }

    @Transactional
    public void cancelBySource(
            ServiceSource serviceSource,
            Long sourceId,
            BillingItemTypes billingItemType,
            String reason
    ) {
        if (serviceSource == null || sourceId == null || billingItemType == null) {
            return;
        }

        patientServiceAndProductRepository
                .findByServiceSourceAndSourceIdAndBillingItemType(
                        serviceSource,
                        sourceId,
                        billingItemType
                )
                .ifPresent(item -> cancel(item.getId(), reason));
    }

    private PatientServiceAndProduct buildEntityFromCreateDto(
            PatientServiceProductCreateDTO dto,
            Long patientId,
            PatientEncounter encounter
    ) {
        long quantity = dto.quantity() == null ? 1L : dto.quantity();

        PatientServiceAndProduct.PatientServiceAndProductBuilder builder = PatientServiceAndProduct.builder()
                .patientId(patientId)
                .encounterId(encounter.getId())
                .billingItemType(dto.billingItemType())
                .brandMedicationId(dto.brandMedicationId())
                .diagnosticTestId(dto.diagnosticTestId())
                .serviceId(dto.serviceId())
                .procedureId(dto.procedureId())
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
                .currency(dto.currency())
                .serviceSource(
                        dto.serviceSource() != null
                                ? dto.serviceSource()
                                : ServiceSource.SERVICE_AND_PRODUCT
                )
                .sourceId(dto.sourceId())
                .notes(dto.notes())
                .isBilled(Boolean.FALSE)
                .billingInvoiceId(null)
                .billingInvoiceItemId(null)
                .isDefaultService(Boolean.FALSE)
                .isExempted(Boolean.FALSE);

        preAuthorizationResolutionService.resolveAndPrepareNewItem(
                builder,
                encounter.getId(),
                dto.billingItemType(),
                dto.procedureId(),
                dto.serviceId(),
                dto.diagnosticTestId(),
                dto.brandMedicationId()
        );

        if (dto.billingItemType() == BillingItemTypes.MEDICATION
                && dto.brandMedicationId() != null) {
            preAuthorizationResolutionService.enrichWaseelSbsMapping(
                    builder,
                    BillingItemTypes.MEDICATION,
                    dto.brandMedicationId()
            );
        }

        PatientServiceAndProduct entity = builder.build();

        if (encounterInsuranceEligibilityService.shouldEvaluatePreAuthorization(encounter.getId())) {
            entity.setPatientInsuranceId(
                    encounterInsuranceEligibilityService.resolveEncounterPatientInsuranceId(
                            encounter.getId()
                    )
            );
            entity.setCoverageStatus(CoverageStatus.COVERED);
        }

        applyResolvedPricing(entity, encounter, quantity);

        return entity;
    }

    private PatientServiceAndProduct buildMedicationEntityFromPrescription(
            PatientPrescription prescription,
            PatientEncounter encounter,
            PatientPrescriptionMedication medication
    ) {
        brandMedicationHelper.validateBrandMedicationExists(medication.getMedicationsId());

        long quantity = 1L;

        PatientServiceAndProduct.PatientServiceAndProductBuilder builder =
                PatientServiceAndProduct.builder()
                        .patientId(prescription.getPatient().getId())
                        .encounterId(encounter.getId())
                        .billingItemType(BillingItemTypes.MEDICATION)
                        .brandMedicationId(medication.getMedicationsId())
                        .serviceSource(ServiceSource.PRESCRIPTION)
                        .sourceId(medication.getId())
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
                        .currency(Currency.SAR)
                        .notes(medication.getInstructions())
                        .isBilled(Boolean.FALSE)
                        .isDefaultService(Boolean.FALSE)
                        .isExempted(Boolean.FALSE);

        preAuthorizationResolutionService.resolveAndPrepareNewItem(
                builder,
                encounter.getId(),
                BillingItemTypes.MEDICATION,
                null,
                null,
                null,
                medication.getMedicationsId()
        );

        preAuthorizationResolutionService.enrichWaseelSbsMapping(
                builder,
                BillingItemTypes.MEDICATION,
                medication.getMedicationsId()
        );

        PatientServiceAndProduct entity = builder.build();

        if (encounterInsuranceEligibilityService.shouldEvaluatePreAuthorization(encounter.getId())) {
            entity.setPatientInsuranceId(
                    encounterInsuranceEligibilityService.resolveEncounterPatientInsuranceId(
                            encounter.getId()
                    )
            );
            entity.setCoverageStatus(CoverageStatus.COVERED);
        }

        applyResolvedPricing(entity, encounter, quantity);
        return entity;
    }

    private void applyResolvedPricing(
            PatientServiceAndProduct item,
            PatientEncounter encounter,
            long quantity
    ) {
        Long facilityId = encounter.getFacilityId();
        if (facilityId == null) {
            throw new BadRequestAlertException(
                    "Encounter facility is required to resolve item pricing.",
                    "patientServicesAndProducts",
                    "encounter.facility.required"
            );
        }

        if (item.getCurrency() == null) {
            throw new BadRequestAlertException(
                    "Currency is required to resolve item pricing.",
                    "patientServicesAndProducts",
                    "currency.required"
            );
        }

        ResolvedBillingPrice resolvedPrice =
                billingEngineService.resolvePricing(item, facilityId);

        BigDecimal unitPrice = resolvedPrice.unitPrice();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));

        item.setUnitPrice(unitPrice);
        item.setCurrency(resolvedPrice.currency());
        item.setTotalAmount(totalAmount);
        item.setGrossAmount(totalAmount);
        item.setNetAmount(totalAmount);
        item.setRemainingAmount(totalAmount);
        item.setPriceSource(mapPriceSource(resolvedPrice.priceSource()));

        LOG.info(
                "[PSP_PRICING] Resolved unitPrice={} currency={} priceSource={} billingItemType={}",
                unitPrice,
                resolvedPrice.currency(),
                resolvedPrice.priceSource(),
                item.getBillingItemType()
        );
    }

    private PriceSource mapPriceSource(BillingPriceSource source) {
        if (source == BillingPriceSource.PRICE_LIST) {
            return PriceSource.PRICE_LIST;
        }

        return PriceSource.DEFAULT;
    }

    private void validateReferences(PatientServiceProductCreateDTO dto) {
        if (dto.diagnosticTestId() != null) {
            diagnosticTestHelper.getDiagnosticTest(dto.diagnosticTestId());
        }

        if (dto.serviceId() != null) {
            serviceHelper.validateServiceExists(dto.serviceId());
        }

        if (dto.procedureId() != null) {
            procedureHelper.validateProcedureExists(dto.procedureId());
        }

        if (dto.brandMedicationId() != null) {
            brandMedicationHelper.validateBrandMedicationExists(dto.brandMedicationId());
        }
    }

    private void validateNoDuplicateEncounterItem(
            PatientServiceProductCreateDTO dto,
            Long encounterId
    ) {
        if (encounterId == null || dto.billingItemType() == null) {
            return;
        }

        if (dto.billingItemType() == BillingItemTypes.MEDICATION
                && dto.brandMedicationId() != null
                && patientServiceAndProductRepository
                        .existsByEncounterIdAndBillingItemTypeAndBrandMedicationId(
                                encounterId,
                                BillingItemTypes.MEDICATION,
                                dto.brandMedicationId()
                        )) {
            throw new BadRequestAlertException(
                    "This medication is already on the encounter. Edit the existing line to change quantity.",
                    "patient_services_and_products",
                    "medication.duplicate"
            );
        }

        if (dto.diagnosticTestId() != null
                && patientServiceAndProductRepository
                        .existsByEncounterIdAndBillingItemTypeAndDiagnosticTestId(
                                encounterId,
                                dto.billingItemType(),
                                dto.diagnosticTestId()
                        )) {
            throw new BadRequestAlertException(
                    "This diagnostic test is already on the encounter.",
                    "patient_services_and_products",
                    "diagnosticTest.duplicate"
            );
        }

        if (dto.billingItemType() == BillingItemTypes.SERVICE
                && dto.serviceId() != null
                && patientServiceAndProductRepository
                        .existsByEncounterIdAndBillingItemTypeAndServiceId(
                                encounterId,
                                BillingItemTypes.SERVICE,
                                dto.serviceId()
                        )) {
            throw new BadRequestAlertException(
                    "This service is already on the encounter.",
                    "patient_services_and_products",
                    "service.duplicate"
            );
        }

        if (dto.billingItemType() == BillingItemTypes.PROCEDURE
                && dto.procedureId() != null
                && patientServiceAndProductRepository
                        .existsByEncounterIdAndBillingItemTypeAndProcedureId(
                                encounterId,
                                BillingItemTypes.PROCEDURE,
                                dto.procedureId()
                        )) {
            throw new BadRequestAlertException(
                    "This procedure is already on the encounter.",
                    "patient_services_and_products",
                    "procedure.duplicate"
            );
        }
    }

    /**
     * Same pre-auth flow as procedures for Medication / Laboratory / Radiology / Service / Procedure PSP lines:
     * - PENDING_APPROVAL → sync Waseel submit (rollback on failure), defer billing
     * - otherwise → bill immediately
     */
    private void completeItemBillingFlow(PatientServiceAndProduct item) {
        if (item == null || item.getEncounterId() == null) {
            return;
        }

        if (item.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL) {
            LOG.info(
                    "[PSP_CREATE] Pre-auth required — submitting to Waseel. encounterId={} pspId={} type={}",
                    item.getEncounterId(),
                    item.getId(),
                    item.getBillingItemType()
            );
            submitPreAuthorizationOrThrow(item.getEncounterId());
            return;
        }

        billItemNow(item);
    }

    private void submitPreAuthorizationOrThrow(Long encounterId) {
        try {
            encounterPreAuthorizationSyncService.submitPendingPreAuthorizationOrThrow(encounterId);
        } catch (BadRequestAlertException ex) {
            String title = ex.getBody() != null && ex.getBody().getTitle() != null
                    ? ex.getBody().getTitle()
                    : ex.getMessage();
            String errorKey = ex.getErrorKey() != null
                    ? ex.getErrorKey()
                    : "preAuthorization.failed";
            throw new BadRequestAlertException(title, "patientServicesAndProducts", errorKey);
        }
    }

    private void billItemNow(PatientServiceAndProduct item) {
        if (item == null || item.getId() == null || item.getEncounterId() == null) {
            return;
        }

        if (Boolean.TRUE.equals(item.getIsBilled())) {
            return;
        }

        PatientEncounter encounter = patientEncounterRepository.findById(item.getEncounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + item.getEncounterId(),
                        "patientServicesAndProducts",
                        "encounter.notfound"
                ));

        Long facilityId = encounter.getFacilityId();
        if (facilityId == null) {
            throw new BadRequestAlertException(
                    "Encounter facility is required to bill this item.",
                    "patientServicesAndProducts",
                    "encounter.facility.required"
            );
        }

        BillingOperationResult result = billingEngineService.onItemOrdered(
                item.getId(),
                facilityId,
                "PSP-CREATE:" + item.getId()
        );

        LOG.info(
                "[PSP_CREATE] Billed item. pspId={} type={} processed={} message={}",
                item.getId(),
                item.getBillingItemType(),
                result.processed(),
                result.message()
        );

        if (!result.processed()) {
            throw new BadRequestAlertException(
                    result.message() == null
                            ? "Billing rule did not match for this item."
                            : result.message(),
                    "patientServicesAndProducts",
                    "billing.failed"
            );
        }
    }

    private BillingEventType resolveBillingEvent(
            PatientServiceProductCreateDTO dto
    ) {
        if (dto.serviceSource() == ServiceSource.CONSULTATION_PORTAL) {
            return BillingEventType.ENCOUNTER_CREATED;
        }

        if (dto.serviceSource() == ServiceSource.LABORATORY
                || dto.serviceSource() == ServiceSource.RADIOLOGY
                || dto.serviceSource() == ServiceSource.PROCEDURE) {
            return BillingEventType.ITEM_ORDERED;
        }

        return BillingEventType.ITEM_ORDERED;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BadRequestAlertException handleConstraintViolation(RuntimeException ex) {
        Throwable root = getRootCause(ex);
        String message = root != null ? root.getMessage() : ex.getMessage();
        String msgLower = message != null ? message.toLowerCase() : "";

        LOG.error("Database constraint violation: {}", message, ex);

        if (msgLower.contains("fk_psp_brand_medication")) {
            return new BadRequestAlertException("brandMedicationNotFound", "patient_services_and_products", "Brand medication does not exist");
        }

        if (msgLower.contains("fk_psp_diagnostic_test")) {
            return new BadRequestAlertException("diagnosticTestNotFound", "patient_services_and_products", "Diagnostic test does not exist");
        }

        if (msgLower.contains("fk_psp_service")) {
            return new BadRequestAlertException("serviceNotFound", "patient_services_and_products", "Service does not exist");
        }

        if (msgLower.contains("fk_psp_procedure")) {
            return new BadRequestAlertException("procedureNotFound", "patient_services_and_products", "Procedure does not exist");
        }

        if (msgLower.contains("fk_psp_billing_invoice")) {
            return new BadRequestAlertException("billingInvoiceNotFound", "patient_services_and_products", "Billing invoice does not exist");
        }

        if (msgLower.contains("fk_psp_billing_invoice_item")) {
            return new BadRequestAlertException("billingInvoiceItemNotFound", "patient_services_and_products", "Billing invoice item does not exist");
        }

        if (msgLower.contains("ck_billing_charge_line_allocation_balance")) {
            return new BadRequestAlertException(
                    "Cannot cancel billing item because charge-line allocation balances are inconsistent.",
                    "patient_services_and_products",
                    "billing.cancel.allocationBalance"
            );
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving patient billing item",
                "patient_services_and_products",
                "db.constraint"
        );
    }

    @Transactional(readOnly = true)
    public Page<PatientServiceAndProduct> findAllServicesAndProductsByEncounterIdAndSource(
            Pageable pageable,
            Long encounterId,
            ServiceSource serviceSource,
            Long sourceId
    ) {
        return patientServiceAndProductRepository
                .findAllByEncounterIdAndServiceSourceAndSourceId(
                        encounterId,
                        serviceSource,
                        sourceId,
                        pageable
                );
    }
}