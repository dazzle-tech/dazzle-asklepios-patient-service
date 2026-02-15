package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patient.PatientCreateDTO;
import com.dazzle.asklepios.service.dto.patient.PatientUpdateDTO;
import com.dazzle.asklepios.service.dto.patient.UnknownPatientCreateDTO;
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
import java.util.List;

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

    public Patient create(PatientCreateDTO dto) {
        LOG.info("[CREATE] Request to create Patient payload={}", dto);

        boolean verified = Boolean.TRUE.equals(dto.isVerified());
        boolean completed = Boolean.TRUE.equals(dto.isCompletedPatient());

        Patient entity = Patient.builder()
                .firstName(dto.firstName())
                .secondName(dto.secondName())
                .thirdName(dto.thirdName())
                .lastName(dto.lastName())

                .sexAtBirth(dto.sexAtBirth())
                .dateOfBirth(dto.dateOfBirth())


                .patientClasses(dto.patientClasses())
                .isPrivatePatient(dto.isPrivatePatient())

                .firstNameSecondaryLang(dto.firstNameSecondaryLang())
                .secondNameSecondaryLang(dto.secondNameSecondaryLang())
                .thirdNameSecondaryLang(dto.thirdNameSecondaryLang())
                .lastNameSecondaryLang(dto.lastNameSecondaryLang())

                .primaryMobileNumber(dto.primaryMobileNumber())
                .secondMobileNumber(dto.secondMobileNumber())
                .homePhone(dto.homePhone())
                .workPhone(dto.workPhone())
                .email(dto.email())
                .receiveSms(dto.receiveSms())
                .receiveEmail(dto.receiveEmail())
                .preferredWayOfContact(dto.preferredWayOfContact())

                .nativeLanguage(dto.nativeLanguage())
                .emergencyContactName(dto.emergencyContactName())
                .emergencyContactRelation(dto.emergencyContactRelation())
                .emergencyContactPhone(dto.emergencyContactPhone())

                .role(dto.role())
                .maritalStatus(dto.maritalStatus())
                .nationality(dto.nationality())
                .religion(dto.religion())
                .ethnicity(dto.ethnicity())
                .occupation(dto.occupation())
                .responsibleParty(dto.responsibleParty())
                .educationalLevel(dto.educationalLevel())

                .previousId(dto.previousId())
                .archivingNumber(dto.archivingNumber())
                .details(dto.details())
                .isUnknown(false)
                .isVerified(verified)
                .isCompletedPatient(completed)
                .build();

        try {
            return patientRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while saving patient (check required fields or unique constraints).",
                    "patient",
                    "db.constraint"
            );
        }
    }

    public Patient createUnknown(UnknownPatientCreateDTO dto) {

    Patient unknownPatient = Patient.builder()
            .isUnknown(true)
            .isVerified(Boolean.TRUE.equals(dto.isVerified()))
            .isCompletedPatient(Boolean.TRUE.equals(dto.isCompletedPatient()))
            .build();

    try {
        Patient createdPatient = patientRepository.saveAndFlush(unknownPatient);

        String mrn = createdPatient.getMedicalRecordNumber();

        if (mrn != null && !mrn.isBlank()) {
            createdPatient.setFirstName("Unknown " + mrn);
            createdPatient.setLastName(null);

            createdPatient = patientRepository.saveAndFlush(createdPatient);
        }

        LOG.info("Created UNKNOWN patient id={} MRN={}",
                createdPatient.getId(),
                createdPatient.getMedicalRecordNumber());

        return createdPatient;

    } catch (DataIntegrityViolationException | JpaSystemException ex) {
        handleConstraintsOnCreateOrUpdate(ex);
        throw new BadRequestAlertException(
                "Database constraint violated while saving patient.",
                "patient",
                "db.constraint"
        );
    }
}


    public Patient update(Long id, PatientUpdateDTO dto) {
        LOG.info("[UPDATE] Request to update Patient id={} payload={}", id, dto);

        Patient existing = patientRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.error("Patient not found with id={}", id);
                    return new NotFoundAlertException(
                            "Patient not found with id " + id,
                            "patient",
                            "notfound"
                    );
                });

        existing.setFirstName(dto.firstName());
        existing.setSecondName(dto.secondName());
        existing.setThirdName(dto.thirdName());
        existing.setLastName(dto.lastName());

        existing.setSexAtBirth(dto.sexAtBirth());
        existing.setDateOfBirth(dto.dateOfBirth());

        existing.setPatientClasses(dto.patientClasses());
        existing.setIsPrivatePatient(dto.isPrivatePatient());

        existing.setFirstNameSecondaryLang(dto.firstNameSecondaryLang());
        existing.setSecondNameSecondaryLang(dto.secondNameSecondaryLang());
        existing.setThirdNameSecondaryLang(dto.thirdNameSecondaryLang());
        existing.setLastNameSecondaryLang(dto.lastNameSecondaryLang());

        existing.setPrimaryMobileNumber(dto.primaryMobileNumber());
        existing.setSecondMobileNumber(dto.secondMobileNumber());
        existing.setHomePhone(dto.homePhone());
        existing.setWorkPhone(dto.workPhone());
        existing.setEmail(dto.email());
        existing.setReceiveSms(dto.receiveSms());
        existing.setReceiveEmail(dto.receiveEmail());
        existing.setPreferredWayOfContact(dto.preferredWayOfContact());

        existing.setNativeLanguage(dto.nativeLanguage());
        existing.setEmergencyContactName(dto.emergencyContactName());
        existing.setEmergencyContactRelation(dto.emergencyContactRelation());
        existing.setEmergencyContactPhone(dto.emergencyContactPhone());

        existing.setRole(dto.role());
        existing.setMaritalStatus(dto.maritalStatus());
        existing.setNationality(dto.nationality());
        existing.setReligion(dto.religion());
        existing.setEthnicity(dto.ethnicity());
        existing.setOccupation(dto.occupation());
        existing.setResponsibleParty(dto.responsibleParty());
        existing.setEducationalLevel(dto.educationalLevel());

        existing.setPreviousId(dto.previousId());
        existing.setArchivingNumber(dto.archivingNumber());

        existing.setDetails(dto.details());
        existing.setIsUnknown(Boolean.TRUE.equals(dto.isUnknown()));
        existing.setIsVerified(Boolean.TRUE.equals(dto.isVerified()));
        existing.setIsCompletedPatient(Boolean.TRUE.equals(dto.isCompletedPatient()));


        existing.setLastModifiedDate(Instant.now());

        try {
            Patient updatedPatient = patientRepository.saveAndFlush(existing);

            LOG.info(
                    "Successfully updated patient id={} (medicalRecordNumber='{}')",
                    updatedPatient.getId(), updatedPatient.getMedicalRecordNumber()
            );
            return updatedPatient;

        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            LOG.error(
                    "Database constraint violation while updating patient id={}: {}",
                    id,
                    exception.getMessage(),
                    exception
            );

            handleConstraintsOnCreateOrUpdate(exception);

            throw new BadRequestAlertException(
                    "Database constraint violated while updating patient (check required fields or unique constraints).",
                    "patient",
                    "db.constraint"
            );
        }
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByMedicalRecordNumber(String medicalRecordNumber, Pageable pageable) {
        LOG.debug(
                "[FIND BY medicalRecordNumber] Searching patients by medicalRecordNumber='{}' pageable={}",
                medicalRecordNumber, pageable
        );
        return patientRepository.findByMedicalRecordNumberContainingIgnoreCase(medicalRecordNumber, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByArchivingNumber(String archivingNumber, Pageable pageable) {
        LOG.debug(
                "[FIND BY ARCHIVING] Searching patients by archivingNumber='{}' pageable={}",
                archivingNumber, pageable
        );
        return patientRepository.findByArchivingNumberContainingIgnoreCase(archivingNumber, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByPrimaryPhone(String primaryPhone, Pageable pageable) {
        LOG.debug(
                "[FIND BY PHONE] Searching patients by primaryPhone='{}' pageable={}",
                primaryPhone, pageable
        );
        return patientRepository.findByPrimaryMobileNumberContaining(primaryPhone, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByDateOfBirth(LocalDate dateOfBirth, Pageable pageable) {
        LOG.debug(
                "[FIND BY DOB] Searching patients by dateOfBirth={} pageable={}",
                dateOfBirth, pageable
        );
        return patientRepository.findByDateOfBirth(dateOfBirth, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Patient> findByFullName(String keyword, Pageable pageable) {
        LOG.debug(
                "[FIND BY NAME] Searching patients by keyword='{}' pageable={}",
                keyword, pageable
        );
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
        Page<PatientDocument> docsPage =
                patientDocumentRepository.findByIsPrimaryTrueAndNumberContainingIgnoreCase(numberPart, pageable);
        return docsPage.map(PatientDocument::getPatient);
    }
    @Transactional(readOnly = true)
    public List<Patient> findByIds(List<Long> ids) {
        LOG.debug("[BULK FIND] Fetching Patients by ids count={} ids={}", ids.size(), ids);
        List<Patient> patients = patientRepository.findAllById(ids);

        LOG.debug("[BULK FIND] Found Patients count={}", patients.size());
        return patients;
    }

    @Transactional(readOnly = true)
    public Patient findById(Long id) {
        LOG.debug("[FIND BY ID] Fetching Patient id={}", id);

        return patientRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.error("Patient not found with id={}", id);
                    return new NotFoundAlertException(
                            "Patient not found with id " + id,
                            "patient",
                            "notfound"
                    );
                });
    }
    @Transactional(readOnly = true)
    public Page<Patient> findByAnyDocumentNumber(String numberPart, Pageable pageable) {
        LOG.debug("[FIND BY ANY DOCUMENT] numberPart='{}' pageable={}", numberPart, pageable);
        Page<PatientDocument> docsPage =
                patientDocumentRepository.findByNumberContainingIgnoreCase(numberPart, pageable);
        return docsPage.map(PatientDocument::getPatient);
    }
    private void handleConstraintsOnCreateOrUpdate(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        String lower = (message != null ? message.toLowerCase() : "");

        if (lower.contains("medical_record_number") || lower.contains("medicalrecordnumber")) {
            if (lower.contains("unique") || lower.contains("duplicate") || lower.contains("already exists")
                    || lower.contains("duplicate key") || lower.contains("duplicate entry")) {
                throw new BadRequestAlertException(
                        "A patient with the same medicalRecordNumber already exists.",
                        "patient",
                        "unique.medical_record_number"
                );
            }
        }

        if (lower.contains("chk_patients_required_fields_when_not_unknown")
                || (lower.contains("check constraint") && lower.contains("unknown"))) {
            throw new BadRequestAlertException(
                    "Required fields are missing for a non-unknown patient.",
                    "patient",
                    "required.fields"
            );
        }


        if (lower.contains("medical_record_number") && (lower.contains("null value") || lower.contains("not-null"))) {
            throw new BadRequestAlertException(
                    "medicalRecordNumber was not generated by the database (check entity mapping to allow DB default).",
                    "patient",
                    "mrn.not.generated"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving patient (check required fields or unique constraints).",
                "patient",
                "db.constraint"
        );
    }
}