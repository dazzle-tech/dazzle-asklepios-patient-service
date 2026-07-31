package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingEventType;
import com.dazzle.asklepios.integration.waseel.service.EncounterInsuranceEligibilityService;
import com.dazzle.asklepios.integration.waseel.service.EncounterPreAuthorizationSyncService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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

    private final BillingRuleEvaluationService billingRuleEvaluationService;

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
            BillingRuleEvaluationService billingRuleEvaluationService
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
        this.billingRuleEvaluationService = billingRuleEvaluationService;
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
                encounter.getId()
        );

        try {
            PatientServiceAndProduct saved = patientServiceAndProductRepository.saveAndFlush(entity);

            submitPreAuthorizationIfPending(saved.getEncounterId());

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
        entity.setUnitPrice(dto.unitPrice());
        entity.setDiscountAmount(defaultZero(dto.discountAmount()));
        entity.setExemptionAmount(defaultZero(dto.exemptionAmount()));
        entity.setTaxAmount(defaultZero(dto.taxAmount()));
        entity.setTotalAmount(dto.totalAmount());
        entity.setCurrency(dto.currency());

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

        if (dto.isBilled() != null) {
            entity.setIsBilled(dto.isBilled());
        }

        entity.setBillingInvoiceId(dto.billingInvoiceId());
        entity.setBillingInvoiceItemId(dto.billingInvoiceItemId());

        try {
            PatientServiceAndProduct updated = patientServiceAndProductRepository.saveAndFlush(entity);

            submitPreAuthorizationIfPending(updated.getEncounterId());

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
                                encounter.getId()
                        );
                    })
                    .toList();

            List<PatientServiceAndProduct> saved = patientServiceAndProductRepository.saveAll(entities);
            patientServiceAndProductRepository.flush();

            saved.stream()
                    .map(PatientServiceAndProduct::getEncounterId)
                    .distinct()
                    .forEach(this::submitPreAuthorizationIfPending);

            LOG.debug("Bulk created Patient billing items : count={}", saved.size());
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    @Transactional
    public void remove(Long id) {
        LOG.debug("Request to delete Patient billing item : {}", id);

        PatientServiceAndProduct entity = patientServiceAndProductRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "idNotFound",
                        "patientServicesAndProducts",
                        "Record not found"
                ));

        if (Boolean.TRUE.equals(entity.getIsBilled())
                || entity.getBillingInvoiceId() != null
                || entity.getBillingInvoiceItemId() != null) {
            throw new BadRequestAlertException(
                    "deleteNotAllowed",
                    "patientServicesAndProducts",
                    "Cannot delete billed item or item linked to invoice"
            );
        }

        patientServiceAndProductRepository.delete(entity);

        LOG.debug("Deleted Patient billing item : id={}", id);
    }

    private PatientServiceAndProduct buildEntityFromCreateDto(
            PatientServiceProductCreateDTO dto,
            Long patientId,
            Long encounterId
    ) {
        long quantity = dto.quantity() == null ? 1L : dto.quantity();
        BigDecimal unitPrice = defaultZero(dto.unitPrice());
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));

        PatientServiceAndProduct.PatientServiceAndProductBuilder builder = PatientServiceAndProduct.builder()
                .patientId(patientId)
                .encounterId(encounterId)
                .billingItemType(dto.billingItemType())
                .brandMedicationId(dto.brandMedicationId())
                .diagnosticTestId(dto.diagnosticTestId())
                .serviceId(dto.serviceId())
                .procedureId(dto.procedureId())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .discountAmount(BigDecimal.ZERO)
                .exemptionAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .grossAmount(totalAmount)
                .netAmount(totalAmount)
                .patientShareAmount(BigDecimal.ZERO)
                .insuranceShareAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .remainingAmount(totalAmount)
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
                encounterId,
                dto.billingItemType(),
                dto.procedureId(),
                dto.serviceId(),
                dto.diagnosticTestId(),
                dto.brandMedicationId()
        );

        return builder.build();
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

    private void submitPreAuthorizationIfPending(Long encounterId) {
        encounterPreAuthorizationSyncService.afterItemPersisted(encounterId);
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

        return new BadRequestAlertException(
                "db.constraint",
                "patient_services_and_products",
                "Database constraint violated while saving patient billing item"
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