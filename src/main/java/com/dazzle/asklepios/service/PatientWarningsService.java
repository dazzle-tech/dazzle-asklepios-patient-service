package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.notification.NotificationClient;
import com.dazzle.asklepios.client.notification.dto.NotificationCreateDTO;
import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.dto.UserDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientWarnings;
import com.dazzle.asklepios.domain.enumeration.PatientWarningStatus;
import com.dazzle.asklepios.domain.enumeration.Severity;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientWarningsRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.PatientWarningCreateDTO;
import com.dazzle.asklepios.service.dto.PatientWarningUpdateDTO;
import com.dazzle.asklepios.service.helper.UserDepartmentHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;


@Service
@Transactional
public class PatientWarningsService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientWarningsService.class);
    private final PatientWarningsRepository patientWarningsRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final NotificationClient notificationClient;
    private final UserDepartmentHelper userDepartmentHelper;

    public PatientWarningsService(PatientWarningsRepository patientWarningsRepository, PatientRepository patientRepository, PatientEncounterRepository patientEncounterRepository, NotificationClient notificationClient, UserDepartmentHelper userDepartmentHelper) {
        this.patientWarningsRepository = patientWarningsRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.notificationClient = notificationClient;
        this.userDepartmentHelper = userDepartmentHelper;
    }

    public PatientWarnings create(PatientWarningCreateDTO patientWarningCreateDTO) {
        LOG.debug("Request to create Patient Warning : {}", patientWarningCreateDTO);

        // Validate onset date rules
        if (patientWarningCreateDTO.onsetDateUndefined() && patientWarningCreateDTO.onsetDate() != null) {
            LOG.debug("The onset date is not null : {}", patientWarningCreateDTO);
            throw new BadRequestAlertException(
                    "onsetDateMustBeNull",
                    "patientWarnings",
                    "Onset Date must be null when onset Date undefined is true"
            );
        }

        if (!patientWarningCreateDTO.onsetDateUndefined() && patientWarningCreateDTO.onsetDate() == null) {
            LOG.debug("The onset date is null : {}", patientWarningCreateDTO);
            throw new BadRequestAlertException(
                    "onsetDateRequired",
                    "patientWarnings",
                    "Onset Date is required when onset Date undefined is false"
            );
        }
        // Validate source-of-information rules
        if (patientWarningCreateDTO.byPatient() && patientWarningCreateDTO.sourceOfInformation() != null) {
            LOG.debug("The source of information is not null : {}", patientWarningCreateDTO);
            throw new BadRequestAlertException(
                    "sourceMustBeNull",
                    "patientWarnings",
                    "source of Information must be null"
            );
        }
        // Validate onset date is not in the future
        if (patientWarningCreateDTO.onsetDate() != null &&
                patientWarningCreateDTO.onsetDate().isAfter(Instant.now())) {

            LOG.debug("The onset date is in the future : {}", patientWarningCreateDTO.onsetDate());

            throw new BadRequestAlertException(
                    "onsetDateInFuture",
                    "patientWarnings",
                    "Onset Date cannot be in the future"
            );
        }
        // Validate source-of-information is provided when not by patient
        if (!patientWarningCreateDTO.byPatient() && patientWarningCreateDTO.sourceOfInformation() == null) {
            LOG.debug("The source of information is null : {}", patientWarningCreateDTO);
            throw new BadRequestAlertException(
                    "sourceRequired",
                    "patientWarnings",
                    "source of Information is required"
            );
        }

        Patient patient = patientRepository.findById(patientWarningCreateDTO.patientId())
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Patient not found with id " + patientWarningCreateDTO.patientId(),
                                "procedure",
                                "patient.notfound"
                        )
                );

        PatientEncounter encounter = patientEncounterRepository.findById(patientWarningCreateDTO.encounterId())
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Encounter not found with id " + patientWarningCreateDTO.encounterId(),
                                "PatientWarnings",
                                "encounter.notfound"
                        )
                );
        PatientWarnings entity = PatientWarnings.builder()
                .patientId(patient.getId())
                .encounterId(encounter.getId())
                .warningType(patientWarningCreateDTO.warningType())
                .warning(patientWarningCreateDTO.warning())
                .severity(patientWarningCreateDTO.severity())
                .onsetDateUndefined(patientWarningCreateDTO.onsetDateUndefined())
                .onsetDate(patientWarningCreateDTO.onsetDate())
                .byPatient(patientWarningCreateDTO.byPatient())
                .sourceOfInformation(patientWarningCreateDTO.sourceOfInformation())
                .note(patientWarningCreateDTO.note())
                .actionTaken(patientWarningCreateDTO.actionTaken())
                .status(patientWarningCreateDTO.status())
                .build();

        try {
            PatientWarnings saved = patientWarningsRepository.save(entity);

            notifyDepartmentUsersForSevereCriticalWarning(saved, patient, encounter);

            LOG.debug("Created Patient Warning: {}", saved);
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            throw handleConstraintViolation(constraintException);
        }
    }


    @Transactional(readOnly = true)
    public Page<PatientWarnings> findAllWarningsByPatientId(
            Pageable pageable,
            boolean showCancelled,
            Long patientId
    ) {
        // Fetch warnings with optional cancellation filter
        Page<PatientWarnings> page;
        if (showCancelled) {
            LOG.debug("Fetch Patient Warning with cancelled allergies");
            page = patientWarningsRepository.findByPatientId(patientId, pageable);
        } else {
            LOG.debug("Fetch Patient Warning without cancelled allergies");
            page = patientWarningsRepository.findByPatientIdAndStatusNot(
                    patientId,
                    PatientWarningStatus.CANCELLED,
                    pageable
            );
        }
        return page;
    }


    @Transactional
    public PatientWarnings cancel(Long id, String reason) {
        LOG.debug("Request to cancel Patient Warning: {}", id);
        String login = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));

        PatientWarnings entity = patientWarningsRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "idnotfound",
                        "patientWarnings",
                        "Patient Warning not found"
                ));

        entity.setStatus(PatientWarningStatus.CANCELLED);
        entity.setCancelledBy(login);
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(reason);

        LOG.debug("Patient Warning cancelled: id={}, cancelledBy={}", id, login);
        return entity;
    }

    @Transactional
    public PatientWarnings resolve(Long id) {
        LOG.debug("Request to resolve Patient Warning : {}", id);

        PatientWarnings entity = patientWarningsRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "idnotfound",
                        "patientWarnings",
                        "Patient Warning not found"
                ));

        String login = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));

        entity.setStatus(PatientWarningStatus.RESOLVED);
        entity.setResolvedBy(login);
        entity.setResolvedDate(Instant.now());

        LOG.debug("Patient Warning resolved: id={}, resolvedBy={}", id, login);
        return entity;
    }

    @Transactional
    public PatientWarnings undoResolve(Long id) {
        LOG.debug("Request to undo resolve Patient Warning : {}", id);

        PatientWarnings entity = patientWarningsRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "idnotfound",
                        "patientWarnings",
                        "Patient Warning not found"
                ));

        if (entity.getStatus() != PatientWarningStatus.RESOLVED) {
            throw new BadRequestAlertException(
                    "Patient Warning is not resolved",
                    "patientWarnings",
                    "notResolved"
            );
        }

        entity.setStatus(PatientWarningStatus.ACTIVE);

        LOG.debug("Patient Warning undo-resolved: id={}", id);
        return entity;
    }


    @Transactional
    public PatientWarnings update(PatientWarningUpdateDTO patientWarningUpdateDTO) {
        LOG.debug("Request to update Patient Warning: {}", patientWarningUpdateDTO);

        PatientWarnings patientWarningObj = patientWarningsRepository.findById(patientWarningUpdateDTO.id())
                .orElseThrow(() -> new BadRequestAlertException(
                        "idNotFound",
                        "patientWarnings",
                        "Patient Warning not found with id " + patientWarningUpdateDTO.id()
                ));

        // Only active warnings can be updated
        if (!(patientWarningObj.getStatus() == PatientWarningStatus.ACTIVE)) {
            LOG.debug("The updated warning status is not active : {}", patientWarningObj.getStatus());
            throw new BadRequestAlertException(
                    "statusMustBeActive",
                    "patientWarnings",
                    "Status must be Active"
            );
        }

        // Validate onset date rules
        if (patientWarningUpdateDTO.onsetDateUndefined() && patientWarningUpdateDTO.onsetDate() != null) {
            LOG.debug("The updated onset date is not null : {}", patientWarningUpdateDTO);
            throw new BadRequestAlertException(
                    "onsetDateMustBeNull",
                    "patientWarnings",
                    "Onset Date must be null when onset Date undefined is true"
            );
        }

        if (!patientWarningUpdateDTO.onsetDateUndefined() && patientWarningUpdateDTO.onsetDate() == null) {
            LOG.debug("The updated onset date is null : {}", patientWarningUpdateDTO);
            throw new BadRequestAlertException(
                    "onsetDateRequired",
                    "patientWarnings",
                    "Onset Date is required when onset Date undefined is false"
            );
        }
        // Validate onset date is not in the future
        if (patientWarningUpdateDTO.onsetDate() != null &&
                patientWarningUpdateDTO.onsetDate().isAfter(Instant.now())) {

            LOG.debug("The updated onset date is in the future : {}", patientWarningUpdateDTO.onsetDate());

            throw new BadRequestAlertException(
                    "onsetDateInFuture",
                    "patientWarnings",
                    "Onset Date cannot be in the future"
            );
        }
        // Validate source-of-information rules
        if (patientWarningUpdateDTO.byPatient() && patientWarningUpdateDTO.sourceOfInformation() != null) {
            LOG.debug("The updated source of information is not null : {}", patientWarningUpdateDTO);
            throw new BadRequestAlertException(
                    "sourceMustBeNull",
                    "patientWarnings",
                    "source of Information must be null"
            );
        }
        if (!patientWarningUpdateDTO.byPatient() && patientWarningUpdateDTO.sourceOfInformation() == null) {
            LOG.debug("The updated source of information is null : {}", patientWarningUpdateDTO);
            throw new BadRequestAlertException(
                    "sourceRequired",
                    "patientWarnings",
                    "source of Information is required"
            );
        }

        // Only allow updates for today's records
        Instant todayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        if (patientWarningObj.getCreatedDate().isBefore(todayStart)) {
            LOG.debug("The created date is before today: {}", patientWarningObj.getCreatedDate());
            throw new BadRequestAlertException(
                    "updateNotAllowed",
                    "patientWarnings",
                    "Only today's records can be updated"
            );
        }

        patientWarningObj.setWarningType(patientWarningUpdateDTO.warningType());
        patientWarningObj.setWarning(patientWarningUpdateDTO.warning());
        patientWarningObj.setSeverity(patientWarningUpdateDTO.severity());
        patientWarningObj.setOnsetDateUndefined(patientWarningUpdateDTO.onsetDateUndefined());
        patientWarningObj.setOnsetDate(patientWarningUpdateDTO.onsetDate());
        patientWarningObj.setByPatient(patientWarningUpdateDTO.byPatient());
        patientWarningObj.setSourceOfInformation(patientWarningUpdateDTO.sourceOfInformation());
        patientWarningObj.setNote(patientWarningUpdateDTO.note());
        patientWarningObj.setActionTaken(patientWarningUpdateDTO.actionTaken());

        try {
            PatientWarnings updated = patientWarningsRepository.saveAndFlush(patientWarningObj);
            Patient patient = patientRepository.findById(updated.getPatientId())
                    .orElseThrow(() ->
                            new NotFoundAlertException(
                                    "Patient not found with id " + updated.getPatientId(),
                                    "procedure",
                                    "patient.notfound"
                            )
                    );

            PatientEncounter encounter = patientEncounterRepository.findById(updated.getEncounterId())
                    .orElseThrow(() ->
                            new NotFoundAlertException(
                                    "Encounter not found with id " + updated.getEncounterId(),
                                    "PatientWarnings",
                                    "encounter.notfound"
                            )
                    );
            notifyDepartmentUsersForSevereCriticalWarning(updated, patient, encounter);

            LOG.debug("Updated Patient Warning: {}", updated);
            return updated;
        } catch (DataIntegrityViolationException | JpaSystemException constraintException) {
            LOG.debug("Constraint violation caught during update");
            throw handleConstraintViolation(constraintException);
        }
    }


    private BadRequestAlertException handleConstraintViolation(RuntimeException constraintException) {
        // Map DB constraint errors to user-friendly messages
        Throwable root = getRootCause(constraintException);
        String message = (root != null ? root.getMessage() : constraintException.getMessage());
        String msgLower = message != null ? message.toLowerCase() : "";

        LOG.error("Database constraint violation while saving patient warning: {}", message, constraintException);

        if (msgLower.contains("uk_patient_warning")) {

            return new BadRequestAlertException(
                    "unique.warning",
                    "patient_warnings",
                    "This warning already exist"
            );
        }

        if (msgLower.contains("fk_patient_warnings_patient_id")) {

            return new BadRequestAlertException(
                    "patientId",
                    "patient_warnings",
                    "The patient does not exist"
            );
        }

        if (msgLower.contains("fk_patient_warnings_encounter_id")) {

            return new BadRequestAlertException(
                    "patientId",
                    "patient_warnings",
                    "The encounter does not exist"
            );
        }

        return new BadRequestAlertException(
                "db.constraint",
                "patient_allergies",
                "Database constraint violated while saving patient warning"
        );
    }

    private void notifyDepartmentUsersForSevereCriticalWarning(PatientWarnings warning, Patient patient, PatientEncounter encounter) {
        if (warning == null || patient == null || encounter == null) {
            return;
        }

        if (!isSevereOrCritical(warning.getSeverity())) {
            return;
        }

        Long departmentId = encounter.getDepartmentId();

        if (departmentId == null) {
            LOG.warn("Skip medical warning notification because encounter department is missing. warningId={}, encounterId={}", warning.getId(), encounter.getId());
            return;
        }

        List<NotificationResolvedRecipientDTO> departmentUsers = buildDepartmentUserRecipients(departmentId);

        if (departmentUsers.isEmpty()) {
            LOG.warn("Skip medical warning notification because no department users found. warningId={}, departmentId={}", warning.getId(), departmentId);
            return;
        }

        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();
        recipientsByRule.put("DEPARTMENT_USERS", departmentUsers);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("patientId", patient.getId());
        data.put("patientName", getPatientName(patient));
        data.put("encounterId", encounter.getId());
        data.put("departmentId", departmentId);
        data.put("warningId", warning.getId());
        data.put("warningType", warning.getWarningType() != null ? warning.getWarningType().toString() : "");
        data.put("warning", warning.getWarning() != null ? warning.getWarning() : "");
        data.put("severity", warning.getSeverity() != null ? warning.getSeverity().toString() : "");
        data.put("onsetDate", warning.getOnsetDate() != null ? warning.getOnsetDate().toString() : "");
        data.put("status", warning.getStatus() != null ? warning.getStatus().toString() : "");

        NotificationCreateDTO dto = new NotificationCreateDTO(null, "MEDICAL_WARNING_SEVERE_CRITICAL", "en", null, recipientsByRule, data, "PATIENT_WARNING", warning.getId());

        try {
            LOG.debug("Creating severe/critical medical warning notification. warningId={}, patientId={}, departmentId={}, recipientsByRule={}", warning.getId(), patient.getId(), departmentId, recipientsByRule);

            notificationClient.createNotification(dto);
        } catch (Exception e) {
            LOG.warn(
                    "Failed to create severe/critical medical warning notification. warningId={}, error={}",
                    warning.getId(),
                    e.getMessage()
            );
        }
    }

    private boolean isSevereOrCritical(Severity severity) {
        if (severity == null) {
            return false;
        }

        return Severity.SEVERE == severity || Severity.CRITICAL == severity;
    }

    private List<NotificationResolvedRecipientDTO> buildDepartmentUserRecipients(Long departmentId) {
        if (departmentId == null) {
            return List.of();
        }

        List<UserDTO> users = userDepartmentHelper.getUsersForDepartment(departmentId);

        if (users == null || users.isEmpty()) {
            return List.of();
        }

        return users.stream()
                .filter(user -> user != null && user.id() != null)
                .map(user -> NotificationResolvedRecipientDTO.builder()
                        .recipientType("USER")
                        .recipientId(user.id())
                        .recipientName(getUserDisplayName(user))
                        .recipientEmail(user.email())
                        .recipientData(Map.of("departmentId", departmentId))
                        .build()
                )
                .toList();
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

    private String getUserDisplayName(UserDTO user) {
        if (user == null) {
            return "";
        }

        String firstName = user.firstName() != null ? user.firstName() : "";
        String lastName = user.lastName() != null ? user.lastName() : "";

        String fullName = (firstName + " " + lastName)
                .replaceAll("\\s+", " ")
                .trim();

        if (!fullName.isBlank()) {
            return fullName;
        }

        if (user.login() != null && !user.login().isBlank()) {
            return user.login();
        }

        if (user.email() != null && !user.email().isBlank()) {
            return user.email();
        }

        return user.id() != null ? String.valueOf(user.id()) : "";
    }
}
