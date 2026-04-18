package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientServiceProduct.PatientServiceProductCreateDTO;
import com.dazzle.asklepios.service.dto.patientServiceProduct.PatientServiceProductUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class PatientServiceAndProductService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientServiceAndProductService.class);

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    public PatientServiceAndProductService(PatientServiceAndProductRepository patientServiceAndProductRepository) {
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
    }

    public PatientServiceAndProduct create(PatientServiceProductCreateDTO dto) {

        LOG.debug("Request to create Patient billing item : {}", dto);


        PatientServiceAndProduct entity = PatientServiceAndProduct.builder()
                .patientId(dto.patientId())
                .encounterId(dto.encounterId())
                .billingItemType(dto.billingItemType())
                .brandMedicationId(dto.brandMedicationId())
                .diagnosticTestId(dto.diagnosticTestId())
                .serviceId(dto.serviceId())
                .procedureId(dto.procedureId())
                .quantity(dto.quantity() == null ? 1L : dto.quantity())
                .unitPrice(dto.unitPrice())
                .currency(dto.currency())
                .serviceSource(dto.serviceSource())
                .isBilled(Boolean.FALSE)
                .billingInvoiceId(null)
                .billingInvoiceItemId(null)
                .build();

        entity.setCreatedDate(Instant.now());
        entity.setCreatedBy(getCurrentUser());
        entity.setLastModifiedDate(Instant.now());
        entity.setLastModifiedBy(getCurrentUser());

        try {
            PatientServiceAndProduct saved = patientServiceAndProductRepository.save(entity);
            LOG.debug("Created Patient billing item : {}", saved);
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<PatientServiceAndProduct> findAllServicesAndProductsByEncounterId(
            Pageable pageable,
            Long encounterId
    ) {
        LOG.debug("Fetch Patient billing items for encounter : {}", encounterId);
        return patientServiceAndProductRepository.findAllByEncounterId(encounterId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PatientServiceAndProduct> findAllServicesAndProductsByPatientId(
            Pageable pageable,
            Long patientId
    ) {

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
        entity.setBrandMedicationId(dto.brandMedicationId());
        entity.setDiagnosticTestId(dto.diagnosticTestId());
        entity.setServiceId(dto.serviceId());
        entity.setProcedureId(dto.procedureId());
        entity.setQuantity(dto.quantity());
        entity.setUnitPrice(dto.unitPrice());
        entity.setDiscountAmount(defaultZero(dto.discountAmount()));
        entity.setExemptionAmount(defaultZero(dto.exemptionAmount()));
        entity.setTaxAmount(defaultZero(dto.taxAmount()));
        entity.setTotalAmount(dto.totalAmount());
        entity.setCurrency(dto.currency());

        if (dto.isBilled() != null) {
            entity.setIsBilled(dto.isBilled());
        }
        entity.setBillingInvoiceId(dto.billingInvoiceId());
        entity.setBillingInvoiceItemId(dto.billingInvoiceItemId());

        entity.setLastModifiedBy(getCurrentUser());
        entity.setLastModifiedDate(Instant.now());

        try {
            PatientServiceAndProduct updated = patientServiceAndProductRepository.saveAndFlush(entity);
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
                        PatientServiceAndProduct entity = PatientServiceAndProduct.builder()
                                .patientId(dto.patientId())
                                .encounterId(dto.encounterId())
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
                                .isBilled(Boolean.FALSE)
                                .billingInvoiceId(null)
                                .billingInvoiceItemId(null)
                                .build();
                        return entity;
                    })
                    .toList();

            List<PatientServiceAndProduct> saved = patientServiceAndProductRepository.saveAll(entities);
            patientServiceAndProductRepository.flush();

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

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BadRequestAlertException handleConstraintViolation(RuntimeException ex) {

        Throwable root = getRootCause(ex);
        String message = (root != null ? root.getMessage() : ex.getMessage());
        String msgLower = message != null ? message.toLowerCase() : "";

        LOG.error("Database constraint violation: {}", message, ex);

        if (msgLower.contains("fk_psp_brand_medication")) {
            return new BadRequestAlertException(
                    "brandMedicationNotFound",
                    "patient_services_and_products",
                    "Brand medication does not exist"
            );
        }

        if (msgLower.contains("fk_psp_diagnostic_test")) {
            return new BadRequestAlertException(
                    "diagnosticTestNotFound",
                    "patient_services_and_products",
                    "Diagnostic test does not exist"
            );
        }

        if (msgLower.contains("fk_psp_service")) {
            return new BadRequestAlertException(
                    "serviceNotFound",
                    "patient_services_and_products",
                    "Service does not exist"
            );
        }

        if (msgLower.contains("fk_psp_procedure")) {
            return new BadRequestAlertException(
                    "procedureNotFound",
                    "patient_services_and_products",
                    "Procedure does not exist"
            );
        }

        if (msgLower.contains("fk_psp_billing_invoice")) {
            return new BadRequestAlertException(
                    "billingInvoiceNotFound",
                    "patient_services_and_products",
                    "Billing invoice does not exist"
            );
        }

        if (msgLower.contains("fk_psp_billing_invoice_item")) {
            return new BadRequestAlertException(
                    "billingInvoiceItemNotFound",
                    "patient_services_and_products",
                    "Billing invoice item does not exist"
            );
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
        LOG.debug(
                "Fetch Patient billing items for encounter : {} and source : {} and sourceId : {}",
                encounterId,
                serviceSource,
                sourceId
        );

        return patientServiceAndProductRepository
                .findAllByEncounterIdAndServiceSourceAndSourceId(
                        encounterId,
                        serviceSource,
                        sourceId,
                        pageable
                );
    }
    private String getCurrentUser() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not authenticated."
                ));
    }
}