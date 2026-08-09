package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiPatientDocumentOption;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class CchiPatientLookupService {

    private static final Logger LOG = LoggerFactory.getLogger(CchiPatientLookupService.class);

    private final PatientRepository patientRepository;
    private final PatientDocumentRepository patientDocumentRepository;

    public CchiPatientLookupService(
            PatientRepository patientRepository,
            PatientDocumentRepository patientDocumentRepository
    ) {
        this.patientRepository = patientRepository;
        this.patientDocumentRepository = patientDocumentRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Patient> findExistingPatient(String documentId) {
        String normalized = normalizeDocumentId(documentId);
        if (normalized == null) {
            return Optional.empty();
        }

        Optional<Patient> byPatientDocumentId = patientRepository.findByDocumentIdIgnoreCase(normalized);
        if (byPatientDocumentId.isPresent()) {
            return byPatientDocumentId;
        }

        return patientDocumentRepository.findFirstByNumberIgnoreCase(normalized)
                .map(PatientDocument::getPatient);
    }

    public Optional<Patient> resolveExistingPatient(String documentId) {
        String normalized = normalizeDocumentId(documentId);
        if (normalized == null) {
            return Optional.empty();
        }

        Optional<Patient> byPatientDocumentId = patientRepository.findByDocumentIdIgnoreCase(normalized);
        if (byPatientDocumentId.isPresent()) {
            return byPatientDocumentId;
        }

        Optional<PatientDocument> patientDocument =
                patientDocumentRepository.findFirstByNumberIgnoreCase(normalized);

        if (patientDocument.isEmpty()) {
            return Optional.empty();
        }

        Patient patient = patientDocument.get().getPatient();
        if (patient == null) {
            return Optional.empty();
        }

        if (patient.getDocumentId() == null || patient.getDocumentId().isBlank()) {
            assertDocumentIdAvailableForPatient(patient, normalized);
            patient.setDocumentId(normalized);
            patientRepository.saveAndFlush(patient);
            LOG.info(
                    "[CCHI] Backfilled patient.documentId={} for patientId={} from patient_documents",
                    normalized,
                    patient.getId()
            );
        }

        return Optional.of(patient);
    }

    @Transactional(readOnly = true)
    public List<CchiPatientDocumentOption> listInsuranceFetchDocumentOptions(Long patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        "patient",
                        "notfound"
                ));

        Map<String, CchiPatientDocumentOption> uniqueOptions = new LinkedHashMap<>();

        String patientLevelDocumentId = normalizeDocumentId(patient.getDocumentId());
        if (patientLevelDocumentId != null) {
            uniqueOptions.put(
                    patientLevelDocumentId.toLowerCase(Locale.ROOT),
                    new CchiPatientDocumentOption(
                            null,
                            patientLevelDocumentId,
                            null,
                            true,
                            null
                    )
            );
        }

        for (PatientDocument document : patientDocumentRepository.findByPatient_IdOrderByIsPrimaryDescIdAsc(patientId)) {
            if (!isEligibleInsuranceFetchDocument(document)) {
                continue;
            }

            String number = normalizeDocumentId(document.getNumber());
            if (number == null) {
                continue;
            }

            String key = number.toLowerCase(Locale.ROOT);
            if (!uniqueOptions.containsKey(key)) {
                uniqueOptions.put(
                        key,
                        new CchiPatientDocumentOption(
                                document.getId(),
                                number,
                                document.getType(),
                                Boolean.TRUE.equals(document.getIsPrimary()),
                                document.getCountryId()
                        )
                );
            }
        }

        return new ArrayList<>(uniqueOptions.values());
    }

    public String resolveInsuranceFetchDocumentId(Patient patient, String selectedDocumentId) {
        if (patient == null) {
            throw new BadRequestAlertException(
                    "Patient is required",
                    "patient",
                    "notfound"
            );
        }

        String normalizedSelection = normalizeDocumentId(selectedDocumentId);
        if (normalizedSelection != null) {
            validateDocumentBelongsToPatient(patient, normalizedSelection);
            return normalizedSelection;
        }

        String patientLevelDocumentId = normalizeDocumentId(patient.getDocumentId());
        if (patientLevelDocumentId != null) {
            return patientLevelDocumentId;
        }

        List<CchiPatientDocumentOption> options = listInsuranceFetchDocumentOptions(patient.getId());
        if (options.isEmpty()) {
            throw new BadRequestAlertException(
                    "Please enter the patient document first before fetching insurance from CCHI",
                    "waseelCchi",
                    "documentId.missing"
            );
        }

        if (options.size() == 1) {
            return options.get(0).documentId();
        }

        throw new BadRequestAlertException(
                "Multiple patient documents found. Select which document ID to use for CCHI insurance fetch.",
                "waseelCchi",
                "cchi.document.selection.required"
        );
    }

    public String resolveDocumentIdForPatient(Patient patient) {
        if (patient == null) {
            return null;
        }

        String fromPatient = normalizeDocumentId(patient.getDocumentId());
        if (fromPatient != null) {
            return fromPatient;
        }

        return patientDocumentRepository.findFirstByPatient_IdAndIsPrimaryTrue(patient.getId())
                .filter(this::isEligibleInsuranceFetchDocument)
                .map(PatientDocument::getNumber)
                .map(this::normalizeDocumentId)
                .or(() -> patientDocumentRepository.findByPatient_IdOrderByIsPrimaryDescIdAsc(patient.getId()).stream()
                        .filter(this::isEligibleInsuranceFetchDocument)
                        .map(PatientDocument::getNumber)
                        .map(this::normalizeDocumentId)
                        .filter(number -> number != null && !number.isBlank())
                        .findFirst())
                .orElse(null);
    }

    public Patient ensureDocumentIdOnPatient(Patient patient, String documentId) {
        if (patient == null) {
            return null;
        }

        String normalized = normalizeDocumentId(documentId);
        if (normalized == null) {
            normalized = resolveDocumentIdForPatient(patient);
        }

        if (normalized == null) {
            return patient;
        }

        String existingDocumentId = normalizeDocumentId(patient.getDocumentId());
        if (existingDocumentId == null || !existingDocumentId.equalsIgnoreCase(normalized)) {
            assertDocumentIdAvailableForPatient(patient, normalized);
            patient.setDocumentId(normalized);
            patient = patientRepository.saveAndFlush(patient);
            LOG.info(
                    "[CCHI] Backfilled patient.documentId={} for patientId={}",
                    normalized,
                    patient.getId()
            );
        }

        return patient;
    }

    public void assertDocumentIdAvailableForPatient(Patient patient, String documentId) {
        String normalized = normalizeDocumentId(documentId);
        if (normalized == null || patient == null || patient.getId() == null) {
            return;
        }

        patientRepository.findByDocumentIdIgnoreCase(normalized)
                .filter(otherPatient -> !otherPatient.getId().equals(patient.getId()))
                .ifPresent(otherPatient -> {
                    String otherMrn = otherPatient.getMedicalRecordNumber();
                    String detail = otherMrn != null && !otherMrn.isBlank()
                            ? "This document ID is already assigned to another patient (MRN: " + otherMrn + ")"
                            : "This document ID is already assigned to another patient (ID: " + otherPatient.getId() + ")";

                    throw new BadRequestAlertException(
                            detail,
                            "patient",
                            "unique.document_id"
                    );
                });
    }

    public Patient markAsCchiPatient(Patient patient) {
        return markAsCchiPatient(patient, null);
    }

    public Patient markAsCchiPatient(Patient patient, String documentId) {
        if (patient == null) {
            return null;
        }

        String resolvedDocumentId = normalizeDocumentId(documentId);
        if (resolvedDocumentId == null) {
            resolvedDocumentId = resolveDocumentIdForPatient(patient);
        }

        if (resolvedDocumentId == null) {
            throw new BadRequestAlertException(
                    "Patient document ID is required before marking as CCHI patient",
                    "patient",
                    "cchi.documentId.required"
            );
        }

        patient = ensureDocumentIdOnPatient(patient, resolvedDocumentId);

        if (!Boolean.TRUE.equals(patient.getIsCchiPatient())) {
            patient.setIsCchiPatient(true);
            patient = patientRepository.saveAndFlush(patient);
            LOG.info(
                    "[CCHI] Marked patientId={} as CCHI patient documentId={}",
                    patient.getId(),
                    resolvedDocumentId
            );
        }

        return patient;
    }

    private void validateDocumentBelongsToPatient(Patient patient, String documentId) {
        String normalized = normalizeDocumentId(documentId);
        if (normalized == null) {
            throw new BadRequestAlertException(
                    "Document ID is required",
                    "waseelCchi",
                    "documentId.required"
            );
        }

        String patientLevelDocumentId = normalizeDocumentId(patient.getDocumentId());
        if (patientLevelDocumentId != null && patientLevelDocumentId.equalsIgnoreCase(normalized)) {
            return;
        }

        boolean belongsToPatient = patientDocumentRepository.findByPatient_IdOrderByIsPrimaryDescIdAsc(patient.getId())
                .stream()
                .filter(this::isEligibleInsuranceFetchDocument)
                .map(PatientDocument::getNumber)
                .map(this::normalizeDocumentId)
                .anyMatch(number -> number != null && number.equalsIgnoreCase(normalized));

        if (!belongsToPatient) {
            throw new BadRequestAlertException(
                    "Selected document ID does not belong to this patient",
                    "waseelCchi",
                    "documentId.invalid"
            );
        }
    }

    private boolean isEligibleInsuranceFetchDocument(PatientDocument document) {
        if (document == null) {
            return false;
        }

        if (document.getType() == DocumentType.NO_DOCUMENT) {
            return false;
        }

        return normalizeDocumentId(document.getNumber()) != null;
    }

    private String normalizeDocumentId(String documentId) {
        if (documentId == null) {
            return null;
        }

        String trimmed = documentId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
