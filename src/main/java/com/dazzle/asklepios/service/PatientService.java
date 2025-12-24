package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.SecurityLevel;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class PatientService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patientRepository;
    private final PatientDocumentRepository patientDocumentRepository;

    public PatientService(
            PatientRepository patientRepository,
            PatientDocumentRepository patientDocumentRepository
    ) {
        this.patientRepository = patientRepository;
        this.patientDocumentRepository = patientDocumentRepository;
    }

    public String generateNextMrn() {
        LOG.debug("Generating next MRN");
        Integer maxNumber = patientRepository.findMaxMrnNumber();
        int nextNumber = (maxNumber == null || maxNumber < 100) ? 100 : maxNumber + 1;
        String mrn = "P" + nextNumber;
        LOG.debug("Generated MRN={} (from maxNumber={})", mrn, maxNumber);
        return mrn;
    }

    public Patient create(Patient patientRequest) {
        LOG.info("[CREATE] Request to create Patient payload={}", patientRequest);

        if (patientRequest == null) {
            LOG.warn("Create Patient called with null payload");
            throw new BadRequestAlertException(
                    "Patient payload is required",
                    "patient",
                    "payload.required"
            );
        }

        Boolean verified = Boolean.TRUE.equals(patientRequest.getIsVerified());
        Boolean incomplete = Boolean.TRUE.equals(patientRequest.getIsCompletedPatient());
        Boolean unknown = Boolean.TRUE.equals(patientRequest.getIsUnknown());

        LOG.debug("Create Patient flags: isVerified={}, isCompletedPatient={}, isUnknown={}", verified, incomplete, unknown);

        String mrn = generateNextMrn();

        Patient entity = Patient.builder()
                .mrn(mrn)

                .firstName(unknown ? "Unknown " + mrn : patientRequest.getFirstName())
                .secondName(unknown ? null : patientRequest.getSecondName())
                .thirdName(unknown ? null : patientRequest.getThirdName())
                .lastName(unknown ? null : patientRequest.getLastName())

                .sexAtBirth(patientRequest.getSexAtBirth())
                .dateOfBirth(patientRequest.getDateOfBirth())
                .securityAccessLevel(
                        (patientRequest.getSecurityAccessLevel() == null
                                || patientRequest.getSecurityAccessLevel().toString().trim().isEmpty())
                                ? SecurityLevel.NORMAL_1
                                : patientRequest.getSecurityAccessLevel()
                )

                .patientClasses(patientRequest.getPatientClasses())
                .isPrivatePatient(patientRequest.getIsPrivatePatient())

                .firstNameSecondaryLang(patientRequest.getFirstNameSecondaryLang())
                .secondNameSecondaryLang(patientRequest.getSecondNameSecondaryLang())
                .thirdNameSecondaryLang(patientRequest.getThirdNameSecondaryLang())
                .lastNameSecondaryLang(patientRequest.getLastNameSecondaryLang())

                .primaryMobileNumber(patientRequest.getPrimaryMobileNumber())
                .secondMobileNumber(patientRequest.getSecondMobileNumber())
                .homePhone(patientRequest.getHomePhone())
                .workPhone(patientRequest.getWorkPhone())
                .email(patientRequest.getEmail())
                .receiveSms(patientRequest.getReceiveSms())
                .receiveEmail(patientRequest.getReceiveEmail())
                .preferredWayOfContact(patientRequest.getPreferredWayOfContact())

                .nativeLanguage(patientRequest.getNativeLanguage())
                .emergencyContactName(patientRequest.getEmergencyContactName())
                .emergencyContactRelation(patientRequest.getEmergencyContactRelation())
                .emergencyContactPhone(patientRequest.getEmergencyContactPhone())

                .role(patientRequest.getRole())
                .maritalStatus(patientRequest.getMaritalStatus())
                .nationality(patientRequest.getNationality())
                .religion(patientRequest.getReligion())
                .ethnicity(patientRequest.getEthnicity())
                .occupation(patientRequest.getOccupation())
                .responsibleParty(patientRequest.getResponsibleParty())
                .educationalLevel(patientRequest.getEducationalLevel())

                .previousId(patientRequest.getPreviousId())
                .archivingNumber(patientRequest.getArchivingNumber())

                .details(patientRequest.getDetails())
                .isUnknown(unknown)
                .isVerified(verified)
                .isCompletedPatient(incomplete)
                .build();

        try {
            Patient saved = patientRepository.saveAndFlush(entity);
            LOG.info("Successfully created patient id={} mrn='{}'", saved.getId(), saved.getMrn());
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            LOG.error("Database constraint violation while creating patient: {}", exception.getMessage(), exception);

            handleConstraintsOnCreateOrUpdate(exception);

            throw new BadRequestAlertException(
                    "Database constraint violated while saving patient (check required fields or unique constraints).",
                    "patient",
                    "db.constraint"
            );
        }
    }

    public Optional<Patient> update(Long id, Patient patientRequest) {
        LOG.info("[UPDATE] Request to update Patient id={} payload={}", id, patientRequest);

        if (patientRequest == null) {
            LOG.warn("Update Patient called with null payload for id={}", id);
            throw new BadRequestAlertException("Patient payload is required", "patient", "payload.required");
        }

        Patient existing = patientRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.error("Patient not found with id={}", id);
                    return new NotFoundAlertException(
                            "Patient not found with id " + id,
                            "patient",
                            "notfound"
                    );
                });

        existing.setFirstName(patientRequest.getFirstName());
        existing.setSecondName(patientRequest.getSecondName());
        existing.setThirdName(patientRequest.getThirdName());
        existing.setLastName(patientRequest.getLastName());

        existing.setSexAtBirth(patientRequest.getSexAtBirth());
        existing.setDateOfBirth(patientRequest.getDateOfBirth());
        existing.setPatientClasses(patientRequest.getPatientClasses());
        existing.setIsPrivatePatient(patientRequest.getIsPrivatePatient());

        existing.setFirstNameSecondaryLang(patientRequest.getFirstNameSecondaryLang());
        existing.setSecondNameSecondaryLang(patientRequest.getSecondNameSecondaryLang());
        existing.setThirdNameSecondaryLang(patientRequest.getThirdNameSecondaryLang());
        existing.setLastNameSecondaryLang(patientRequest.getLastNameSecondaryLang());

        existing.setPrimaryMobileNumber(patientRequest.getPrimaryMobileNumber());
        existing.setSecondMobileNumber(patientRequest.getSecondMobileNumber());
        existing.setHomePhone(patientRequest.getHomePhone());
        existing.setWorkPhone(patientRequest.getWorkPhone());
        existing.setEmail(patientRequest.getEmail());
        existing.setReceiveSms(patientRequest.getReceiveSms());
        existing.setReceiveEmail(patientRequest.getReceiveEmail());
        existing.setPreferredWayOfContact(patientRequest.getPreferredWayOfContact());

        existing.setNativeLanguage(patientRequest.getNativeLanguage());
        existing.setEmergencyContactName(patientRequest.getEmergencyContactName());
        existing.setEmergencyContactRelation(patientRequest.getEmergencyContactRelation());
        existing.setEmergencyContactPhone(patientRequest.getEmergencyContactPhone());

        existing.setRole(patientRequest.getRole());
        existing.setMaritalStatus(patientRequest.getMaritalStatus());
        existing.setNationality(patientRequest.getNationality());
        existing.setReligion(patientRequest.getReligion());
        existing.setEthnicity(patientRequest.getEthnicity());
        existing.setOccupation(patientRequest.getOccupation());
        existing.setResponsibleParty(patientRequest.getResponsibleParty());
        existing.setEducationalLevel(patientRequest.getEducationalLevel());

        existing.setPreviousId(patientRequest.getPreviousId());
        existing.setArchivingNumber(patientRequest.getArchivingNumber());

        existing.setDetails(patientRequest.getDetails());
        existing.setIsUnknown(patientRequest.getIsUnknown());

        existing.setIsVerified(Boolean.TRUE.equals(patientRequest.getIsVerified()));
        existing.setIsCompletedPatient(Boolean.TRUE.equals(patientRequest.getIsCompletedPatient()));

        existing.setLastModifiedBy(patientRequest.getLastModifiedBy());
        existing.setLastModifiedDate(Instant.now());

        try {
            Patient updated = patientRepository.saveAndFlush(existing);
            LOG.info("Successfully updated patient id={} (mrn='{}')", updated.getId(), updated.getMrn());
            return Optional.of(updated);

        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            LOG.error("Database constraint violation while updating patient id={}: {}", id, exception.getMessage(), exception);

            handleConstraintsOnCreateOrUpdate(exception);

            throw new BadRequestAlertException(
                    "Database constraint violated while updating patient (check required fields or unique constraints).",
                    "patient",
                    "db.constraint"
            );
        }
    }

    @Transactional(readOnly = true)
    public Page<Patient> findAll(Pageable pageable) {
        LOG.debug("[FIND ALL] Fetching all patients with pageable={}", pageable);
        return patientRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByMrn(String mrn, Pageable pageable) {
        LOG.debug("[FIND BY MRN] Searching patients by mrn='{}' with pageable={}", mrn, pageable);
        return patientRepository.findByMrnContainingIgnoreCase(mrn, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByArchivingNumber(String archivingNumber, Pageable pageable) {
        LOG.debug("[FIND BY ARCHIVING] Searching patients by archivingNumber='{}' with pageable={}", archivingNumber, pageable);
        return patientRepository.findByArchivingNumberContainingIgnoreCase(archivingNumber, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByPrimaryPhone(String primaryPhone, Pageable pageable) {
        LOG.debug("[FIND BY PHONE] Searching patients by primaryPhone='{}' with pageable={}", primaryPhone, pageable);
        return patientRepository.findByPrimaryMobileNumberContaining(primaryPhone, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByDateOfBirth(LocalDate dateOfBirth, Pageable pageable) {
        LOG.debug("[FIND BY DOB] Searching patients by dateOfBirth={} with pageable={}", dateOfBirth, pageable);
        return patientRepository.findByDateOfBirth(dateOfBirth, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByFullName(String keyword, Pageable pageable) {
        LOG.debug("[FIND BY NAME] Searching patients by keyword='{}' with pageable={}", keyword, pageable);
        return patientRepository
                .findByFirstNameContainingIgnoreCaseOrSecondNameContainingIgnoreCaseOrThirdNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                        keyword, keyword, keyword, keyword, pageable
                );
    }

    @Transactional(readOnly = true)
    public Page<Patient> findUnknownPatients(Pageable pageable) {
        LOG.debug("[FIND UNKNOWN] Fetching unknown patients with pageable={}", pageable);
        return patientRepository.findByIsUnknownTrue(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByPrimaryDocumentNumber(String numberPart, Pageable pageable) {
        LOG.debug("[FIND BY PRIMARY DOCUMENT] numberPart='{}' pageable={}", numberPart, pageable);

        if (numberPart == null || numberPart.isBlank()) {
            LOG.warn("findByPrimaryDocumentNumber called with empty numberPart");
            throw new BadRequestAlertException(
                    "Document number fragment is required",
                    "patient",
                    "number.required"
            );
        }

        Page<PatientDocument> docsPage =
                patientDocumentRepository.findByIsPrimaryTrueAndNumberContainingIgnoreCase(numberPart, pageable);

        return docsPage.map(PatientDocument::getPatient);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByAnyDocumentNumber(String numberPart, Pageable pageable) {
        LOG.debug("[FIND BY ANY DOCUMENT] numberPart='{}' pageable={}", numberPart, pageable);

        if (numberPart == null || numberPart.isBlank()) {
            LOG.warn("findByAnyDocumentNumber called with empty numberPart");
            throw new BadRequestAlertException(
                    "Document number fragment is required",
                    "patient",
                    "number.required"
            );
        }

        Page<PatientDocument> docsPage =
                patientDocumentRepository.findByNumberContainingIgnoreCase(numberPart, pageable);

        return docsPage.map(PatientDocument::getPatient);
    }

    private void handleConstraintsOnCreateOrUpdate(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());
        String lower = (message != null ? message.toLowerCase() : "");

        LOG.error("Database constraint violation while saving patient: {}", message, exception);

        if (lower.contains("mrn") && (
                lower.contains("ux") ||
                        lower.contains("unique constraint") ||
                        lower.contains("duplicate key") ||
                        lower.contains("duplicate entry")
        )) {
            throw new BadRequestAlertException(
                    "A patient with the same MRN already exists.",
                    "patient",
                    "unique.mrn"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving patient (check required fields or unique constraints).",
                "patient",
                "db.constraint"
        );
    }
}
