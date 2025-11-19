package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.SecurityLevel;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class PatientService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public String generateNextMrn() {
        Integer maxNumber = patientRepository.findMaxMrnNumber();
        int nextNumber = (maxNumber == null || maxNumber < 100) ? 100 : maxNumber + 1;
        return "P" + nextNumber;
    }


    public Patient create(Patient incoming) {
        LOG.info("[CREATE] Request to create Patient payload={}", incoming);

        if (incoming == null) {
            throw new BadRequestAlertException("Patient payload is required", "patient", "payload.required");
        }

        Boolean verified = Boolean.TRUE.equals(incoming.getIsVerified());
        Boolean incomplete = Boolean.TRUE.equals(incoming.getIsCompletedPatient());

        Patient entity = Patient.builder()
                .mrn(generateNextMrn())

                .firstName(incoming.getFirstName())
                .secondName(incoming.getSecondName())
                .thirdName(incoming.getThirdName())
                .lastName(incoming.getLastName())

                .sexAtBirth(incoming.getSexAtBirth())
                .dateOfBirth(incoming.getDateOfBirth())
                .securityAccessLevel(
                        (incoming.getSecurityAccessLevel() == null
                                || incoming.getSecurityAccessLevel().toString().trim().isEmpty())
                                ? SecurityLevel.NORMAL_1
                                : incoming.getSecurityAccessLevel()
                )


                .patientClasses(incoming.getPatientClasses())
                .isPrivatePatient(incoming.getIsPrivatePatient())

                .firstNameSecondaryLang(incoming.getFirstNameSecondaryLang())
                .secondNameSecondaryLang(incoming.getSecondNameSecondaryLang())
                .thirdNameSecondaryLang(incoming.getThirdNameSecondaryLang())
                .lastNameSecondaryLang(incoming.getLastNameSecondaryLang())

                .primaryMobileNumber(incoming.getPrimaryMobileNumber())
                .secondMobileNumber(incoming.getSecondMobileNumber())
                .homePhone(incoming.getHomePhone())
                .workPhone(incoming.getWorkPhone())
                .email(incoming.getEmail())
                .receiveSms(incoming.getReceiveSms())
                .receiveEmail(incoming.getReceiveEmail())
                .preferredWayOfContact(incoming.getPreferredWayOfContact())

                .nativeLanguage(incoming.getNativeLanguage())
                .emergencyContactName(incoming.getEmergencyContactName())
                .emergencyContactRelation(incoming.getEmergencyContactRelation())
                .emergencyContactPhone(incoming.getEmergencyContactPhone())

                .role(incoming.getRole())
                .maritalStatus(incoming.getMaritalStatus())
                .nationality(incoming.getNationality())
                .religion(incoming.getReligion())
                .ethnicity(incoming.getEthnicity())
                .occupation(incoming.getOccupation())
                .responsibleParty(incoming.getResponsibleParty())
                .educationalLevel(incoming.getEducationalLevel())

                .previousId(incoming.getPreviousId())
                .archivingNumber(incoming.getArchivingNumber())

                .details(incoming.getDetails())
                .isUnknown(incoming.getIsUnknown())
                .isVerified(verified)
                .isCompletedPatient(incomplete)
                .build();

        try {
            Patient saved = patientRepository.saveAndFlush(entity);
            LOG.info("Successfully created patient id={} mrn='{}'", saved.getId(), saved.getMrn());
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            handleConstraintsOnCreateOrUpdate(constraintException);

            throw new BadRequestAlertException(
                    "Database constraint violated while saving patient (check required fields or unique constraints).",
                    "patient",
                    "db.constraint"
            );
        }
    }


    public Optional<Patient> update(Long id, Patient incoming) {
        LOG.info("[UPDATE] Request to update Patient id={} payload={}", id, incoming);

        if (incoming == null) {
            throw new BadRequestAlertException("Patient payload is required", "patient", "payload.required");
        }

        Patient existing = patientRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + id,
                        "patient",
                        "notfound"
                ));


        existing.setFirstName(incoming.getFirstName());
        existing.setSecondName(incoming.getSecondName());
        existing.setThirdName(incoming.getThirdName());
        existing.setLastName(incoming.getLastName());

        existing.setSexAtBirth(incoming.getSexAtBirth());
        existing.setDateOfBirth(incoming.getDateOfBirth());
        existing.setPatientClasses(incoming.getPatientClasses());
        existing.setIsPrivatePatient(incoming.getIsPrivatePatient());

        existing.setFirstNameSecondaryLang(incoming.getFirstNameSecondaryLang());
        existing.setSecondNameSecondaryLang(incoming.getSecondNameSecondaryLang());
        existing.setThirdNameSecondaryLang(incoming.getThirdNameSecondaryLang());
        existing.setLastNameSecondaryLang(incoming.getLastNameSecondaryLang());

        existing.setPrimaryMobileNumber(incoming.getPrimaryMobileNumber());
        existing.setSecondMobileNumber(incoming.getSecondMobileNumber());
        existing.setHomePhone(incoming.getHomePhone());
        existing.setWorkPhone(incoming.getWorkPhone());
        existing.setEmail(incoming.getEmail());
        existing.setReceiveSms(incoming.getReceiveSms());
        existing.setReceiveEmail(incoming.getReceiveEmail());
        existing.setPreferredWayOfContact(incoming.getPreferredWayOfContact());

        existing.setNativeLanguage(incoming.getNativeLanguage());
        existing.setEmergencyContactName(incoming.getEmergencyContactName());
        existing.setEmergencyContactRelation(incoming.getEmergencyContactRelation());
        existing.setEmergencyContactPhone(incoming.getEmergencyContactPhone());

        existing.setRole(incoming.getRole());
        existing.setMaritalStatus(incoming.getMaritalStatus());
        existing.setNationality(incoming.getNationality());
        existing.setReligion(incoming.getReligion());
        existing.setEthnicity(incoming.getEthnicity());
        existing.setOccupation(incoming.getOccupation());
        existing.setResponsibleParty(incoming.getResponsibleParty());
        existing.setEducationalLevel(incoming.getEducationalLevel());

        existing.setPreviousId(incoming.getPreviousId());
        existing.setArchivingNumber(incoming.getArchivingNumber());

        existing.setDetails(incoming.getDetails());
        existing.setIsUnknown(incoming.getIsUnknown());

        existing.setIsVerified(Boolean.TRUE.equals(incoming.getIsVerified()));
        existing.setIsCompletedPatient(Boolean.TRUE.equals(incoming.getIsCompletedPatient()));

        existing.setLastModifiedBy(incoming.getLastModifiedBy());
        existing.setLastModifiedDate(Instant.now());

        try {
            Patient updated = patientRepository.saveAndFlush(existing);
            LOG.info("Successfully updated patient id={} (mrn='{}')", updated.getId(), updated.getMrn());
            return Optional.of(updated);
        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            handleConstraintsOnCreateOrUpdate(constraintException);

            throw new BadRequestAlertException(
                    "Database constraint violated while updating patient (check required fields or unique constraints).",
                    "patient",
                    "db.constraint"
            );
        }
    }


    @Transactional(readOnly = true)
    public Page<Patient> findAll(Pageable pageable) {
        LOG.debug("Fetching paged Patients pageable={}", pageable);
        return patientRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByMrn(String mrn, Pageable pageable) {
        LOG.debug("Fetching Patients by MRN like='{}'", mrn);
        return patientRepository.findByMrnContainingIgnoreCase(mrn, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByArchivingNumber(String archivingNumber, Pageable pageable) {
        LOG.debug("Fetching Patients by archivingNumber like='{}'", archivingNumber);
        return patientRepository.findByArchivingNumberContainingIgnoreCase(archivingNumber, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByPrimaryPhone(String primaryPhone, Pageable pageable) {
        LOG.debug("Fetching Patients by primary phone like='{}'", primaryPhone);
        return patientRepository.findByPrimaryMobileNumberContaining(primaryPhone, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByDateOfBirth(LocalDate dateOfBirth, Pageable pageable) {
        LOG.debug("Fetching Patients by dateOfBirth={}", dateOfBirth);
        return patientRepository.findByDateOfBirth(dateOfBirth, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByFullName(String keyword, Pageable pageable) {
        LOG.debug("Fetching Patients by full name keyword='{}'", keyword);
        return patientRepository
                .findByFirstNameContainingIgnoreCaseOrSecondNameContainingIgnoreCaseOrThirdNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                        keyword, keyword, keyword, keyword, pageable
                );
    }


    private void handleConstraintsOnCreateOrUpdate(RuntimeException constraintException) {
        Throwable root = getRootCause(constraintException);
        String message = (root != null ? root.getMessage() : constraintException.getMessage());
        String lower = (message != null ? message.toLowerCase() : "");

        LOG.error("Database constraint violation while saving patient: {}", message, constraintException);

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
