package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public String resolveDocumentIdForPatient(Patient patient) {
        if (patient == null) {
            return null;
        }

        String fromPatient = normalizeDocumentId(patient.getDocumentId());
        if (fromPatient != null) {
            return fromPatient;
        }

        return patientDocumentRepository.findFirstByPatient_IdAndIsPrimaryTrue(patient.getId())
                .map(PatientDocument::getNumber)
                .map(this::normalizeDocumentId)
                .or(() -> patientDocumentRepository.findFirstByPatient_IdOrderByIdAsc(patient.getId())
                        .map(PatientDocument::getNumber)
                        .map(this::normalizeDocumentId))
                .orElse(null);
    }

    public void markAsCchiPatient(Patient patient) {
        if (patient == null || Boolean.TRUE.equals(patient.getIsCchiPatient())) {
            return;
        }

        patient.setIsCchiPatient(true);
        patientRepository.saveAndFlush(patient);
        LOG.info("[CCHI] Marked patientId={} as CCHI patient", patient.getId());
    }

    private String normalizeDocumentId(String documentId) {
        if (documentId == null) {
            return null;
        }

        String trimmed = documentId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
