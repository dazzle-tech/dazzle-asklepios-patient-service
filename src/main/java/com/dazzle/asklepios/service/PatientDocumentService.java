package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentCategory;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class PatientDocumentService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientDocumentService.class);

    private final PatientDocumentRepository patientDocumentRepository;
    private final EntityManager entityManager;

    public PatientDocumentService(
            PatientDocumentRepository patientDocumentRepository,
            EntityManager entityManager
    ) {
        this.patientDocumentRepository = patientDocumentRepository;
        this.entityManager = entityManager;
    }

    public PatientDocument create(Long patientId, PatientDocument incoming) {
        LOG.info("[CREATE] Request to create PatientDocument patientId={} payload={}", patientId, incoming);

        if (incoming == null) {
            throw new BadRequestAlertException("PatientDocument payload is required", "patientDocument", "payload.required");
        }
        if (patientId == null) {
            throw new BadRequestAlertException("Patient id is required", "patientDocument", "patient.required");
        }

        PatientDocument entity = PatientDocument.builder()
                .patient(refPatient(patientId))
                .countryId(incoming.getCountryId())
                .category(incoming.getCategory())
                .type(incoming.getType())
                .number(incoming.getNumber())
                .build();

        try {
            PatientDocument saved = patientDocumentRepository.saveAndFlush(entity);
            LOG.info("Successfully created PatientDocument id={} for patientId={}", saved.getId(), patientId);
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            throw handleConstraintViolation(constraintException);
        }
    }

    public Optional<PatientDocument> update(Long id, Long patientId, PatientDocument incoming) {
        LOG.info("[UPDATE] Request to update PatientDocument id={} patientId={} payload={}", id, patientId, incoming);

        if (incoming == null) {
            throw new BadRequestAlertException("PatientDocument payload is required", "patientDocument", "payload.required");
        }
        if (patientId == null) {
            throw new BadRequestAlertException("Patient id is required", "patientDocument", "patient.required");
        }

        PatientDocument existing = patientDocumentRepository.findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException("PatientDocument not found with id " + id,
                                "patientDocument",
                                "notfound")
                );

        existing.setPatient(refPatient(patientId));
        existing.setCountryId(incoming.getCountryId());
        existing.setCategory(incoming.getCategory());
        existing.setType(incoming.getType());
        existing.setNumber(incoming.getNumber());

        try {
            PatientDocument updated = patientDocumentRepository.saveAndFlush(existing);
            LOG.info("Successfully updated PatientDocument id={}", updated.getId());
            return Optional.of(updated);
        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            throw handleConstraintViolation(constraintException);
        }
    }

    @Transactional(readOnly = true)
    public Page<PatientDocument> findAll(Pageable pageable) {
        return patientDocumentRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<PatientDocument> getSecondaryDocumentsByPatient(Long patientId, Pageable pageable) {
        if (patientId == null) {
            throw new BadRequestAlertException("Patient id is required", "patientDocument", "patient.required");
        }
        return patientDocumentRepository.findByPatientIdAndCategory(patientId, DocumentCategory.SECONDARY, pageable);
    }

    @Transactional(readOnly = true)
    public List<PatientDocument> getPrimaryDocumentsByPatient(Long patientId) {
        if (patientId == null) {
            throw new BadRequestAlertException("Patient id is required", "patientDocument", "patient.required");
        }
        return patientDocumentRepository.findByPatientIdAndCategory(patientId, DocumentCategory.PRIMARY);
    }

    public boolean delete(Long id) {
        if (id == null) return false;
        if (!patientDocumentRepository.existsById(id)) return false;

        try {
            patientDocumentRepository.deleteById(id);
            return true;
        } catch (Exception ex) {
            LOG.error("Error deleting PatientDocument id={}", id, ex);
            return false;
        }
    }
    private Patient refPatient(Long patientId) {
        return entityManager.getReference(Patient.class, patientId);
    }
    private RuntimeException handleConstraintViolation(Exception ex) {
        Throwable root = getRootCause(ex);
        String msg = root != null ? root.getMessage() : ex.getMessage();
        String msgLower = msg != null ? msg.toLowerCase() : "";

        LOG.error("Constraint violation while saving PatientDocument: {}", msg, ex);

        if (msgLower.contains("ux_patient_documents_number_type_country") ||
                msgLower.contains("ux_patient_documents_patient_type_country") ||
                msgLower.contains("duplicate key") ||
                msgLower.contains("duplicate") ||
                msgLower.contains("unique")) {

            return new BadRequestAlertException(
                    "A document with the same number, type, and country already exists.",
                    "patientDocument",
                    "unique.document"
            );
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving patient document.",
                "patientDocument",
                "db.constraint"
        );
    }
}
