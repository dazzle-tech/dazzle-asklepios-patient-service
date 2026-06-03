package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.PayorPlanItemClient;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationSubmissionService;
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
import feign.FeignException;
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
    private final PayorPlanItemClient payorPlanItemClient;
    private final PreAuthorizationSubmissionService preAuthorizationSubmissionService;

    public PatientServiceAndProductService(
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            PatientRepository patientRepository,
            PatientEncounterRepository patientEncounterRepository,
            DiagnosticTestHelper diagnosticTestHelper,
            ServiceHelper serviceHelper,
            ProcedureHelper procedureHelper,
            BrandMedicationHelper brandMedicationHelper,
            PayorPlanItemClient payorPlanItemClient,
            PreAuthorizationSubmissionService preAuthorizationSubmissionService
    ) {
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.diagnosticTestHelper = diagnosticTestHelper;
        this.serviceHelper = serviceHelper;
        this.procedureHelper = procedureHelper;
        this.brandMedicationHelper = brandMedicationHelper;
        this.payorPlanItemClient = payorPlanItemClient;
        this.preAuthorizationSubmissionService = preAuthorizationSubmissionService;
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

        PreAuthorizationStatus preAuthorizationStatus =
                resolvePreAuthorizationStatus(
                        dto.billingItemType(),
                        dto.procedureId(),
                        dto.serviceId(),
                        dto.diagnosticTestId(),
                        dto.brandMedicationId()
                );

        PatientServiceAndProduct entity = PatientServiceAndProduct.builder()
                .patientId(patient.getId())
                .encounterId(encounter.getId())
                .billingItemType(dto.billingItemType())
                .brandMedicationId(dto.brandMedicationId())
                .diagnosticTestId(dto.diagnosticTestId())
                .serviceId(dto.serviceId())
                .procedureId(dto.procedureId())
                .quantity(dto.quantity() == null ? 1L : dto.quantity())
                .unitPrice(dto.unitPrice())
                .currency(dto.currency())
                .serviceSource(dto.serviceSource())
                .sourceId(dto.sourceId())
                .notes(dto.notes())
                .preAuthorizationStatus(preAuthorizationStatus)
                .isBilled(Boolean.FALSE)
                .billingInvoiceId(null)
                .billingInvoiceItemId(null)
                .build();

        try {
            PatientServiceAndProduct saved = patientServiceAndProductRepository.saveAndFlush(entity);

            submitPreAuthorizationIfPending(saved);

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

        entity.setPreAuthorizationStatus(
                resolvePreAuthorizationStatus(
                        dto.billingItemType(),
                        dto.procedureId(),
                        dto.serviceId(),
                        dto.diagnosticTestId(),
                        dto.brandMedicationId()
                )
        );

        if (dto.isBilled() != null) {
            entity.setIsBilled(dto.isBilled());
        }

        entity.setBillingInvoiceId(dto.billingInvoiceId());
        entity.setBillingInvoiceItemId(dto.billingInvoiceItemId());

        try {
            PatientServiceAndProduct updated = patientServiceAndProductRepository.saveAndFlush(entity);

            submitPreAuthorizationIfPending(updated);

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

                        PreAuthorizationStatus preAuthorizationStatus =
                                resolvePreAuthorizationStatus(
                                        dto.billingItemType(),
                                        dto.procedureId(),
                                        dto.serviceId(),
                                        dto.diagnosticTestId(),
                                        dto.brandMedicationId()
                                );

                        return PatientServiceAndProduct.builder()
                                .patientId(patient.getId())
                                .encounterId(encounter.getId())
                                .billingItemType(dto.billingItemType())
                                .brandMedicationId(dto.brandMedicationId())
                                .diagnosticTestId(dto.diagnosticTestId())
                                .serviceId(dto.serviceId())
                                .procedureId(dto.procedureId())
                                .quantity(dto.quantity() == null ? 1L : dto.quantity())
                                .unitPrice(dto.unitPrice())
                                .currency(dto.currency())
                                .sourceId(dto.sourceId())
                                .serviceSource(dto.serviceSource())
                                .notes(dto.notes())
                                .preAuthorizationStatus(preAuthorizationStatus)
                                .isBilled(Boolean.FALSE)
                                .billingInvoiceId(null)
                                .billingInvoiceItemId(null)
                                .build();
                    })
                    .toList();

            List<PatientServiceAndProduct> saved = patientServiceAndProductRepository.saveAll(entities);
            patientServiceAndProductRepository.flush();

            saved.stream()
                    .filter(item -> item.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL)
                    .map(PatientServiceAndProduct::getEncounterId)
                    .distinct()
                    .forEach(preAuthorizationSubmissionService::submitIfRequired);

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

    private PreAuthorizationStatus resolvePreAuthorizationStatus(
            BillingItemTypes billingItemType,
            Long procedureId,
            Long serviceId,
            Long diagnosticTestId,
            Long brandMedicationId
    ) {
        if (billingItemType == BillingItemTypes.PROCEDURE && procedureId != null) {
            return requiresPreAuthorizationForProcedure(procedureId)
                    ? PreAuthorizationStatus.PENDING_APPROVAL
                    : PreAuthorizationStatus.NOT_REQUIRED;
        }

        if (billingItemType == BillingItemTypes.SERVICE && serviceId != null) {
            return requiresPreAuthorizationForService(serviceId)
                    ? PreAuthorizationStatus.PENDING_APPROVAL
                    : PreAuthorizationStatus.NOT_REQUIRED;
        }

        if ((billingItemType == BillingItemTypes.LABORATORY
                || billingItemType == BillingItemTypes.RADIOLOGY
                || billingItemType == BillingItemTypes.PATHOLOGY)
                && diagnosticTestId != null) {
            return requiresPreAuthorizationForDiagnosticTest(diagnosticTestId)
                    ? PreAuthorizationStatus.PENDING_APPROVAL
                    : PreAuthorizationStatus.NOT_REQUIRED;
        }

        if (billingItemType == BillingItemTypes.MEDICATION && brandMedicationId != null) {
            return requiresPreAuthorizationForMedication(brandMedicationId)
                    ? PreAuthorizationStatus.PENDING_APPROVAL
                    : PreAuthorizationStatus.NOT_REQUIRED;
        }

        return PreAuthorizationStatus.NOT_REQUIRED;
    }

    private boolean requiresPreAuthorizationForProcedure(Long procedureId) {
        try {
            return Boolean.TRUE.equals(
                    payorPlanItemClient.requiresPreAuthorizationForProcedure(procedureId)
            );
        } catch (FeignException ex) {
            LOG.error(
                    "[SETUP_SERVICE] Failed to check procedure pre-authorization. procedureId={} status={} body={}",
                    procedureId,
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );
            return false;
        }
    }

    private boolean requiresPreAuthorizationForService(Long serviceId) {
        try {
            return Boolean.TRUE.equals(
                    payorPlanItemClient.requiresPreAuthorizationForService(serviceId)
            );
        } catch (FeignException ex) {
            LOG.error(
                    "[SETUP_SERVICE] Failed to check service pre-authorization. serviceId={} status={} body={}",
                    serviceId,
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );
            return false;
        }
    }

    private boolean requiresPreAuthorizationForDiagnosticTest(Long diagnosticTestId) {
        try {
            return Boolean.TRUE.equals(
                    payorPlanItemClient.requiresPreAuthorizationForDiagnosticTest(diagnosticTestId)
            );
        } catch (FeignException ex) {
            LOG.error(
                    "[SETUP_SERVICE] Failed to check diagnostic test pre-authorization. diagnosticTestId={} status={} body={}",
                    diagnosticTestId,
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );
            return false;
        }
    }

    private boolean requiresPreAuthorizationForMedication(Long brandMedicationId) {
        try {
            return Boolean.TRUE.equals(
                    payorPlanItemClient.requiresPreAuthorizationForMedication(brandMedicationId)
            );
        } catch (FeignException ex) {
            LOG.error(
                    "[SETUP_SERVICE] Failed to check medication pre-authorization. brandMedicationId={} status={} body={}",
                    brandMedicationId,
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );
            return false;
        }
    }

    private void submitPreAuthorizationIfPending(PatientServiceAndProduct item) {
        if (item != null && item.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL) {
            preAuthorizationSubmissionService.submitIfRequired(item.getEncounterId());
        }
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