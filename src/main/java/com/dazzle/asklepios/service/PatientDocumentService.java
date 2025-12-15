package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
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

    public PatientDocument create(Long patientId, PatientDocument patientDocumentRequest) {
        LOG.info("[CREATE] Request to create PatientDocument patientId={} payload={}", patientId, patientDocumentRequest);

        if (patientDocumentRequest == null) {
            throw new BadRequestAlertException("PatientDocument payload is required", "patientDocument", "payload.required");
        }
        if (patientId == null) {
            throw new BadRequestAlertException("Patient id is required", "patientDocument", "patient.required");
        }

//        if (patientDocumentRequest.getType() == DocumentType.NO_DOCUMENT) {
//            patientDocumentRequest.setCountryId(null);
//            patientDocumentRequest.setNumber(null);
//        } else {
//            if (patientDocumentRequest.getCountryId() == null) {
//                throw new BadRequestAlertException("Country is required", "patientDocument", "country.required");
//            }
//            if (patientDocumentRequest.getNumber() == null) {
//                throw new BadRequestAlertException("Number is required", "patientDocument", "number.required");
//            }
//        }

        PatientDocument entity = PatientDocument.builder()
                .patient(refPatient(patientId))
                .countryId(patientDocumentRequest.getCountryId())
                .type(patientDocumentRequest.getType())
                .number(patientDocumentRequest.getNumber())
                .isPrimary(Boolean.TRUE.equals(patientDocumentRequest.getIsPrimary()))
                .build();

        try {
            PatientDocument saved = patientDocumentRepository.saveAndFlush(entity);
            LOG.info("Successfully created PatientDocument id={} for patientId={}", saved.getId(), patientId);
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            throw handleConstraintViolation(constraintException);
        }
    }

    public Optional<PatientDocument> update(Long id, Long patientId, PatientDocument patientDocumentRequest) {
        LOG.info("[UPDATE] Request to update PatientDocument id={} patientId={} payload={}", id, patientId, patientDocumentRequest);

        if (patientDocumentRequest == null) {
            throw new BadRequestAlertException("PatientDocument payload is required", "patientDocument", "payload.required");
        }
        if (patientId == null) {
            throw new BadRequestAlertException("Patient id is required", "patientDocument", "patient.required");
        }

        PatientDocument existing = patientDocumentRepository.findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "PatientDocument not found with id " + id,
                                "patientDocument",
                                "notfound"
                        )
                );

//        if (patientDocumentRequest.getType() == DocumentType.NO_DOCUMENT) {
//            existing.setCountryId(null);
//            existing.setNumber(null);
//        } else {
//            if (patientDocumentRequest.getCountryId() == null) {
//                throw new BadRequestAlertException("Country is required", "patientDocument", "country.required");
//            }
//            if (patientDocumentRequest.getNumber() == null) {
//                throw new BadRequestAlertException("Number is required", "patientDocument", "number.required");
//            }
//            existing.setCountryId(patientDocumentRequest.getCountryId());
//            existing.setNumber(patientDocumentRequest.getNumber());
//        }

        existing.setPatient(refPatient(patientId));
        existing.setType(patientDocumentRequest.getType());
        existing.setIsPrimary(Boolean.TRUE.equals(patientDocumentRequest.getIsPrimary()));

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
        LOG.debug("[FIND ALL] Fetching all PatientDocuments with pageable={}", pageable);
        return patientDocumentRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<PatientDocument> getDocumentsByPatient(Long patientId, Pageable pageable) {
        LOG.debug("[FIND BY PATIENT] Fetching PatientDocuments for patientId={} with pageable={}", patientId, pageable);

        if (patientId == null) {
            throw new BadRequestAlertException("Patient id is required", "patientDocument", "patient.required");
        }
        return patientDocumentRepository.findByPatientId(patientId, pageable);
    }

    public boolean delete(Long id) {
        LOG.info("[DELETE] Request to delete PatientDocument id={}", id);

        if (id == null) {
            LOG.warn("Delete request for PatientDocument with null id");
            return false;
        }
        if (!patientDocumentRepository.existsById(id)) {
            LOG.warn("PatientDocument id={} does not exist, nothing to delete", id);
            return false;
        }

        try {
            patientDocumentRepository.deleteById(id);
            LOG.info("Successfully deleted PatientDocument id={}", id);
            return true;
        } catch (Exception exception) {
            LOG.error("Error deleting PatientDocument id={}", id, exception);
            return false;
        }
    }

    private Patient refPatient(Long patientId) {
        return entityManager.getReference(Patient.class, patientId);
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.error("Constraint violation while saving PatientDocument: {}", message, exception);

        if (messageLower.contains("ux_patient_documents_primary_per_patient")) {
            return new BadRequestAlertException(
                    "This patient already has a primary document.",
                    "patientDocument",
                    "primary.exists"
            );
        }

        if (messageLower.contains("ux_patient_documents_number_type_country")) {
            return new BadRequestAlertException(
                    "A document with the same number already exists for this type and country.",
                    "patientDocument",
                    "document.number.duplicate"
            );
        }

        if (messageLower.contains("ux_patient_documents_patient_type_country")) {
            return new BadRequestAlertException(
                    "This patient already has a document of the same type for the selected country.",
                    "patientDocument",
                    "document.type.country.exists"
            );
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving patient document.",
                "patientDocument",
                "db.constraint"
        );
    }
}
