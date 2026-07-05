package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.SystemConfigurationClient;
import com.dazzle.asklepios.domain.DuplicationCandidate;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.SystemConfigKey;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import com.dazzle.asklepios.repository.DuplicationCandidateRepository;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patient.PatientCreateDTO;
import com.dazzle.asklepios.service.dto.patient.PatientDuplicationLookupDTO;
import com.dazzle.asklepios.service.dto.patient.PatientUpdateDTO;
import com.dazzle.asklepios.service.dto.patient.UnknownPatientCreateDTO;
import com.dazzle.asklepios.service.helper.NotificationHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.InvalidPasswordException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.errors.PatientAlreadyActiveException;
import com.dazzle.asklepios.web.rest.vm.patient.CreatePasswordKeyValidationVM;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
@RequiredArgsConstructor
public class PatientService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patientRepository;
    private final PatientDocumentRepository patientDocumentRepository;
    private final DuplicationCandidateRepository duplicationCandidateRepository;
    private static final long CREATE_PASSWORD_KEY_EXPIRATION_HOURS = 24;
    private final PasswordEncoder passwordEncoder;
    private static final Pattern STRONG_PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#_.-])[A-Za-z\\d@$!%*?&#_.-]{8,}$"
    );
    private final SystemConfigurationClient systemConfigurationClient;
    private final NotificationHelper notificationHelper;

    @Value("${application.asklepios-application-url}")
    private String asklepiosApplicationlUrl;


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
                .securityAccessLevel(dto.securityAccessLevel())
                .activated(false)
                .bloodGroup(dto.bloodGroup())
                .patientConditions(dto.patientConditions())
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
                .activated(false)
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
        existing.setSecurityAccessLevel(dto.securityAccessLevel());
        if (dto.bloodGroup() != null) {
            existing.setBloodGroup(dto.bloodGroup());
        }
        existing.setPatientConditions(dto.patientConditions());

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
        String[] tokens = keyword.trim().split("\\s+");
        Specification<Patient> spec = (root, query, cb) -> {
            List<Predicate> tokenPredicates = new ArrayList<>();
            for (String token : tokens) {
                String pattern = "%" + token.toLowerCase() + "%";
                tokenPredicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern),
                        cb.like(cb.lower(root.get("secondName")), pattern),
                        cb.like(cb.lower(root.get("thirdName")), pattern),
                        cb.like(cb.lower(root.get("lastName")), pattern),
                        cb.like(cb.lower(root.get("firstNameSecondaryLang")), pattern),
                        cb.like(cb.lower(root.get("secondNameSecondaryLang")), pattern),
                        cb.like(cb.lower(root.get("thirdNameSecondaryLang")), pattern),
                        cb.like(cb.lower(root.get("lastNameSecondaryLang")), pattern)
                ));
            }
            return cb.and(tokenPredicates.toArray(new Predicate[0]));
        };
        return patientRepository.findAll(spec, pageable);
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
        return patientRepository
                .findDistinctByPatientDocuments_NumberContainingIgnoreCase(numberPart, pageable);
    }

    public Page<Patient> findDuplicationCandidates(PatientDuplicationLookupDTO duplicationLookupDTO, Pageable pageable) {
        if (duplicationLookupDTO == null || duplicationLookupDTO.ruleId() == null) {
            return Page.empty(pageable);
        }

        DuplicationCandidate duplicationCandidate = duplicationCandidateRepository
                .findByIdAndIsActiveTrue(duplicationLookupDTO.ruleId())
                .orElse(null);

        if (duplicationCandidate == null || duplicationCandidate.getFields() == null || duplicationCandidate.getFields().isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<Patient> spec = buildDuplicationSpec(duplicationCandidate.getFields(), duplicationLookupDTO);
        return patientRepository.findAll(spec, pageable);
    }

    @Transactional
    public void sendCreatePasswordEmailToPatient(Long patientId) {
        Patient patient = patientRepository
                .findById(patientId)
                .orElseThrow(() -> new BadRequestAlertException("notfound", "patient", "Patient not found"));

        if (patient.getEmail() == null || patient.getEmail().trim().isEmpty()) {
            throw new BadRequestAlertException("email.missing", "patient", "Patient email is missing");
        }

        Instant now = Instant.now();

        String token = patient.getResetKey();

        boolean hasValidToken =
                token != null &&
                        patient.getResetDate() != null &&
                        patient.getResetDate().isAfter(now.minus(24, ChronoUnit.HOURS));

        if (!hasValidToken) {
            token = RandomUtil.generateResetKey();
            patient.setResetKey(token);
            patient.setResetDate(now);
        }

        patientRepository.saveAndFlush(patient);

        PatientDocument primaryDoc = patientDocumentRepository
                .findByPatientIdAndIsPrimaryTrue(patient.getId())
                .orElse(null);

        if (primaryDoc == null) {
            LOG.warn("Primary document not found for patient id={}", patient.getId());
            throw new BadRequestAlertException(
                    "primary.document.missing",
                    "patient",
                    "Primary document is missing for patient"
            );
        }

        String language = patient.getNativeLanguage() != null
                ? patient.getNativeLanguage()
                : "en";

        String patientName = getPatientName(patient);

        String createPasswordUrl =
                asklepiosApplicationlUrl + "/create-patient-password?key=" + token;

        String logoUrl = systemConfigurationClient.getResolvedValue(SystemConfigKey.SYSTEM_LOGO);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("patientName", patientName);
        data.put("documentNumber", primaryDoc.getNumber());
        data.put("patientEmail", patient.getEmail());
        data.put("token", token);
        data.put("createPasswordUrl", createPasswordUrl);
        data.put("title", "CMS | Set your password");
        data.put("logoUrl", logoUrl);

        String login = SecurityUtils.getCurrentUserLogin().orElse(null);
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = notificationHelper.resolveRecipients(null, login, patient.getCreatedBy(), patient, null);

        try {
            notificationHelper.sendNotification(null,
                    NotificationCode.PATIENT_CREATE_PASSWORD,
                    language,
                    recipientsByRule,
                    data,
                    "PATIENT",
                    patient.getId());
        } catch (Exception e) {
            LOG.warn(
                    "Failed to create patient create-password notification. patientId={}, error={}",
                    patient.getId(),
                    e.getMessage()
            );
        }
    }

    @Transactional(readOnly = true)
    public CreatePasswordKeyValidationVM validateCreatePasswordKey(String key) {
        return patientRepository
                .findOneByResetKey(key)
                .map(patient -> {
                    boolean activated = Boolean.TRUE.equals(patient.isActivated());
                    boolean passwordAlreadySet = patient.getPassword() != null;

                    boolean notExpired =
                            patient.getResetDate() != null &&
                                    patient.getResetDate().isAfter(
                                            Instant.now().minus(CREATE_PASSWORD_KEY_EXPIRATION_HOURS, ChronoUnit.HOURS)
                                    );

                    boolean valid = notExpired && !activated;

                    String message;
                    if (!notExpired) message = "TOKEN_INVALID_OR_EXPIRED";
                    else if (activated) message = "PATIENT_ALREADY_ACTIVE";
                    else message = "OK";

                    return new CreatePasswordKeyValidationVM(valid, activated, passwordAlreadySet, message);
                })
                .orElse(new CreatePasswordKeyValidationVM(false, false, false, "TOKEN_NOT_FOUND"));
    }

    @Transactional
    public Optional<Patient> completeCreatePassword(String newPassword, String key) {
        if (!isPasswordSecure(newPassword)) {
            throw new InvalidPasswordException();
        }

        return patientRepository
                .findOneByResetKey(key)
                .filter(patient -> patient.getResetDate() != null)
                .filter(patient -> patient.getResetDate().isAfter(
                        Instant.now().minus(CREATE_PASSWORD_KEY_EXPIRATION_HOURS, ChronoUnit.HOURS)
                ))
                .map(patient -> {
                    if (Boolean.TRUE.equals(patient.isActivated())) {
                        throw new PatientAlreadyActiveException();
                    }

                    patient.setPassword(passwordEncoder.encode(newPassword));
                    patient.setActivated(true);

                    // one-time use
                    patient.setResetKey(null);
                    patient.setResetDate(null);

                    return patientRepository.save(patient);
                });
    }

    private static boolean isPasswordSecure(String password) {
        return password != null && STRONG_PASSWORD_PATTERN.matcher(password).matches();
    }

    private final class RandomUtil {
        private static final int DEF_COUNT = 20;
        private static final SecureRandom SECURE_RANDOM = new SecureRandom();

        private RandomUtil() {
        }

        public static String generateRandomAlphanumericString() {
            return RandomStringUtils.random(20, 0, 0, true, true, null, SECURE_RANDOM);
        }

        public static String generateResetKey() {
            return generateRandomAlphanumericString();
        }

        static {
            SECURE_RANDOM.nextBytes(new byte[64]);
        }
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

    private Specification<Patient> buildDuplicationSpec(Map<String, Boolean> fields, PatientDuplicationLookupDTO duplicationLookupDTO) {
        return (patientRoot, criteriaQuery, criteriaBuilder) -> {

            LOG.debug("=== [DUPLICATION SPEC BUILD START] ===");
            LOG.debug("Incoming DTO => ruleId={}, firstName={}, lastName={}, gender={}, dob={}, documentNo={}",
                    duplicationLookupDTO.ruleId(),
                    duplicationLookupDTO.firstName(),
                    duplicationLookupDTO.lastName(),
                    duplicationLookupDTO.gender(),
                    duplicationLookupDTO.dateOfBirth(),
                    duplicationLookupDTO.documentNo()
            );

            LOG.debug("Active Rule Fields => {}", fields);

            List<Predicate> preds = new ArrayList<>();

            if (Boolean.TRUE.equals(fields.get("DOB"))) {
                LOG.debug("Checking DOB field...");
                if (duplicationLookupDTO.dateOfBirth() == null) {
                    LOG.debug("DOB is required by rule but DTO has null → returning disjunction");
                    return criteriaBuilder.disjunction();
                }

                LOG.debug("Comparing dateOfBirth DB column with value={}", duplicationLookupDTO.dateOfBirth());

                preds.add(
                        criteriaBuilder.equal(
                                patientRoot.get("dateOfBirth"),
                                duplicationLookupDTO.dateOfBirth()
                        )
                );
            }

            if (Boolean.TRUE.equals(fields.get("GENDER"))) {
                LOG.debug("Checking GENDER field...");
                if (duplicationLookupDTO.gender() == null || duplicationLookupDTO.gender().isBlank()) {
                    LOG.debug("GENDER is required by rule but DTO has blank/null → returning disjunction");
                    return criteriaBuilder.disjunction();
                }

                LOG.debug("Comparing sexAtBirth with value={}", duplicationLookupDTO.gender().trim());
                preds.add(criteriaBuilder.equal(patientRoot.get("sexAtBirth"), duplicationLookupDTO.gender().trim()));
            }

            if (Boolean.TRUE.equals(fields.get("FIRST_NAME"))) {
                LOG.debug("Checking FIRST_NAME field...");
                if (duplicationLookupDTO.firstName() == null || duplicationLookupDTO.firstName().isBlank()) {
                    LOG.debug("FIRST_NAME is required but DTO empty → returning disjunction");
                    return criteriaBuilder.disjunction();
                }

                LOG.debug("Comparing firstName (lowercase) with value={}",
                        duplicationLookupDTO.firstName().trim().toLowerCase());

                preds.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(patientRoot.get("firstName")),
                        duplicationLookupDTO.firstName().trim().toLowerCase()
                ));
            }

            if (Boolean.TRUE.equals(fields.get("LAST_NAME"))) {
                LOG.debug("Checking LAST_NAME field...");
                if (duplicationLookupDTO.lastName() == null || duplicationLookupDTO.lastName().isBlank()) {
                    LOG.debug("LAST_NAME required but DTO empty → returning disjunction");
                    return criteriaBuilder.disjunction();
                }

                LOG.debug("Comparing lastName (lowercase) with value={}",
                        duplicationLookupDTO.lastName().trim().toLowerCase());

                preds.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(patientRoot.get("lastName")),
                        duplicationLookupDTO.lastName().trim().toLowerCase()
                ));
            }

            if (Boolean.TRUE.equals(fields.get("DOCUMENT_NO"))) {
                LOG.debug("Checking DOCUMENT_NO field...");
                if (duplicationLookupDTO.documentNo() == null || duplicationLookupDTO.documentNo().isBlank()) {
                    LOG.debug("DOCUMENT_NO required but DTO empty → returning disjunction");
                    return criteriaBuilder.disjunction();
                }

                LOG.debug("Comparing primary document number with value={}",
                        duplicationLookupDTO.documentNo().trim());

                Subquery<Long> docSubquery = criteriaQuery.subquery(Long.class);
                Root<PatientDocument> docRoot = docSubquery.from(PatientDocument.class);
                docSubquery.select(docRoot.get("patient").get("id"))
                        .where(
                                criteriaBuilder.equal(docRoot.get("isPrimary"), Boolean.TRUE),
                                criteriaBuilder.equal(docRoot.get("number"), duplicationLookupDTO.documentNo().trim())
                        );
                preds.add(patientRoot.get("id").in(docSubquery));
            }

            LOG.debug("Total predicates added: {}", preds.size());
            LOG.debug("=== [DUPLICATION SPEC BUILD END] ===");

            return criteriaBuilder.and(preds.toArray(new Predicate[0]));
        };
    }

    private String getPatientName(Patient patient) {
        if (patient == null) {
            return "";
        }

        String firstName = patient.getFirstName() != null ? patient.getFirstName() : "";
        String secondName = patient.getSecondName() != null ? patient.getSecondName() : "";
        String thirdName = patient.getThirdName() != null ? patient.getThirdName() : "";
        String lastName = patient.getLastName() != null ? patient.getLastName() : "";

        String fullName = (firstName + " " + secondName + " " + thirdName + " " + lastName)
                .replaceAll("\\s+", " ")
                .trim();

        if (!fullName.isBlank()) {
            return fullName;
        }

        if (patient.getEmail() != null && !patient.getEmail().isBlank()) {
            return patient.getEmail();
        }

        return patient.getId() != null ? String.valueOf(patient.getId()) : "";
    }

}