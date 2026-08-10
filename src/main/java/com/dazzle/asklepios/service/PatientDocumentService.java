package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.CountryName;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patientDocuments.PatientDocumentCreateDTO;
import com.dazzle.asklepios.service.dto.patientDocuments.PatientDocumentUpdateDTO;
import com.dazzle.asklepios.service.dto.patientDocuments.PatientNoDocumentCreateDTO;
import com.dazzle.asklepios.integration.waseel.service.CchiPatientLookupService;
import com.dazzle.asklepios.service.helper.CountryHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
    private final PatientRepository patientRepository;
    private final CountryHelper countryHelper;
    private final CchiPatientLookupService cchiPatientLookupService;

    @PersistenceContext
    private EntityManager entityManager;

    public PatientDocumentService(
            PatientDocumentRepository patientDocumentRepository,
            PatientRepository patientRepository,
            CountryHelper countryHelper,
            CchiPatientLookupService cchiPatientLookupService) {
        this.patientDocumentRepository = patientDocumentRepository;
        this.patientRepository = patientRepository;
        this.countryHelper = countryHelper;
        this.cchiPatientLookupService = cchiPatientLookupService;
    }

    public PatientDocument create(PatientDocumentCreateDTO dto) {
        LOG.info("[CREATE] Request to create PatientDocument payload={}", dto);

        if (dto.countryId() != null) {
            CountryName countryName = countryHelper.getCountryName(dto.countryId());
            validateDocumentNumber(dto.type(), countryName, dto.number());
        }

        boolean isFirstDocument = !patientDocumentRepository.existsByPatientId(dto.patientId());

        PatientDocument entity = PatientDocument.builder()
                .patient(refPatient(dto.patientId()))
                .countryId(dto.countryId())
                .type(dto.type())
                .number(dto.number())
                .isPrimary(isFirstDocument)
                .build();

        try {
            PatientDocument saved = patientDocumentRepository.saveAndFlush(entity);
            syncPatientDocumentId(saved);

            LOG.info(
                    "Successfully created PatientDocument id={} for patientId={} isPrimary={}",
                    saved.getId(),
                    dto.patientId(),
                    saved.getIsPrimary()
            );

            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    public PatientDocument createNoDocument(PatientNoDocumentCreateDTO dto) {
        LOG.info("[CREATE NO_DOCUMENT] Request payload={}", dto);
        PatientDocument entity = PatientDocument.builder()
                .patient(refPatient(dto.patientId()))
                .countryId(null)
                .type(DocumentType.NO_DOCUMENT)
                .number(null)
                .isPrimary(Boolean.TRUE.equals(dto.isPrimary()))
                .build();

        try {
            PatientDocument saved = patientDocumentRepository.saveAndFlush(entity);
            LOG.info(
                    "Successfully created NO_DOCUMENT PatientDocument id={} for patientId={}",
                    saved.getId(),
                    dto.patientId()
            );
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    public PatientDocument update(Long id, PatientDocumentUpdateDTO dto) {
        LOG.info("[UPDATE] Request to update PatientDocument id={} payload={}", id, dto);

        if (dto.countryId() != null) {
            CountryName countryName = countryHelper.getCountryName(dto.countryId());
            validateDocumentNumber(dto.type(), countryName, dto.number());
        }

        PatientDocument existing = patientDocumentRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientDocument not found with id " + id,
                        "patientDocument",
                        "notfound"
                ));

        existing.setPatient(refPatient(dto.patientId()));
        existing.setCountryId(dto.countryId());
        existing.setType(dto.type());
        existing.setNumber(dto.number());
        existing.setIsPrimary(Boolean.TRUE.equals(dto.isPrimary()));

        try {
            PatientDocument updated = patientDocumentRepository.saveAndFlush(existing);
            syncPatientDocumentId(updated);
            LOG.info("Successfully updated PatientDocument id={}", updated.getId());
            return updated;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    @Transactional(readOnly = true)
    public Optional<PatientDocument> getPrimaryDocumentByPatientId(Long patientId) {
        LOG.debug("[FIND PRIMARY DOCUMENT BY PATIENT] patientId={}", patientId);

        return patientDocumentRepository.findByPatientIdAndIsPrimaryTrue(patientId);
    }

    @Transactional(readOnly = true)
    public Page<PatientDocument> getDocumentsByPatient(Long patientId, Pageable pageable) {
        LOG.debug(
                "[FIND BY PATIENT] patientId={} pageable={}",
                patientId,
                pageable
        );
        return patientDocumentRepository.findByPatientId(patientId, pageable);
    }

    public boolean delete(Long id) {
        LOG.info("[DELETE] Request to delete PatientDocument id={}", id);

        try {
            PatientDocument document = patientDocumentRepository.findById(id)
                    .orElseThrow(() -> new NotFoundAlertException(
                            "PatientDocument not found with id " + id,
                            "patientDocument",
                            "notfound"
                    ));

            if (Boolean.TRUE.equals(document.getIsPrimary())) {
                throw new BadRequestAlertException(
                        "The primary document cannot be deleted.",
                        "patientDocument",
                        "primary.cannot.delete"
                );
            }

            patientDocumentRepository.delete(document);

            LOG.info("Successfully deleted PatientDocument id={}", id);
            return true;

        } catch (BadRequestAlertException | NotFoundAlertException ex) {
            throw ex;

        } catch (Exception ex) {
            LOG.error("Error deleting PatientDocument id={}", id, ex);
            return false;
        }
    }

    // ─── Private Helpers ───────────────────────────────────────────────────────

    private void validateDocumentNumber(DocumentType type, CountryName countryCode, String number) {
        if (type == null || type == DocumentType.NO_DOCUMENT) return;
        if (countryCode != CountryName.SAUDI_ARABIA) return;

        String num = (number == null) ? "" : number.trim();

        switch (type) {
            case NATIONAL_ID -> {
                if (!num.startsWith("1")) {
                    throw new BadRequestAlertException(
                            "Saudi National ID number must start with 1.",
                            "patientDocument",
                            "number.invalid.start"
                    );
                }
                if (num.length() < 10) {
                    throw new BadRequestAlertException(
                            "ID number should not be less than 10 digits.",
                            "patientDocument",
                            "number.invalid.length"
                    );
                }
            }
            case IQAMA, BORDER_NUMBER -> {
                if (!num.startsWith("2")) {
                    throw new BadRequestAlertException(
                            "Saudi " + type.name() + " number must start with 2.",
                            "patientDocument",
                            "number.invalid.start"
                    );
                }
                if (num.length() < 10) {
                    throw new BadRequestAlertException(
                            "ID number should not be less than 10 digits.",
                            "patientDocument",
                            "number.invalid.length"
                    );
                }
            }
            default -> { }
        }
    }

    private Patient refPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        "patient",
                        "notfound"
                ));
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String lower = message != null ? message.toLowerCase() : "";

        LOG.error("Constraint violation while saving PatientDocument: {}", message, exception);

        if (lower.contains("ux_patient_documents_primary_per_patient")) {
            return new BadRequestAlertException(
                    "This patient already has a primary document.",
                    "patientDocument",
                    "primary.exists"
            );
        }

        if (lower.contains("ux_patient_documents_number_type_country")) {
            return new BadRequestAlertException(
                    "A document with the same number already exists for this type and country.",
                    "patientDocument",
                    "document.number.duplicate"
            );
        }

        if (lower.contains("ux_patient_documents_patient_type_country")) {
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

    private void syncPatientDocumentId(PatientDocument document) {
        if (document == null || document.getPatient() == null) {
            return;
        }

        String number = document.getNumber();
        if (number == null || number.isBlank()) {
            return;
        }

        Patient patient = document.getPatient();
        if (patient.getDocumentId() != null && !patient.getDocumentId().isBlank()) {
            return;
        }

        String normalizedDocumentId = number.trim();
        cchiPatientLookupService.assertDocumentIdAvailableForPatient(patient, normalizedDocumentId);
        patient.setDocumentId(normalizedDocumentId);
        patientRepository.saveAndFlush(patient);
        LOG.info(
                "[PATIENT_DOCUMENT] Backfilled patient.documentId={} for patientId={} from documentId={}",
                patient.getDocumentId(),
                patient.getId(),
                document.getId()
        );
    }
}