package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.notification.NotificationClient;
import com.dazzle.asklepios.client.notification.dto.NotificationCreateDTO;
import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.dto.UserDTO;
import com.dazzle.asklepios.domain.PainAssessment;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.PainLevel;
import com.dazzle.asklepios.domain.enumeration.Severity;
import com.dazzle.asklepios.repository.PainAssessmentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.painAssessment.PainAssessmentCreateDTO;
import com.dazzle.asklepios.service.dto.painAssessment.PainAssessmentUpdateDTO;
import com.dazzle.asklepios.service.helper.UserDepartmentHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class PainAssessmentService {

    private static final Logger LOG = LoggerFactory.getLogger(PainAssessmentService.class);

    private final PainAssessmentRepository painAssessmentRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final NotificationClient notificationClient;
    private final UserDepartmentHelper userDepartmentHelper;

    public PainAssessment create(PainAssessmentCreateDTO dto) {
        LOG.info("[CREATE] PainAssessment payload={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "painAssessment",
                        "patient.notfound"
                ));
        PatientEncounter encounter = patientEncounterRepository.findById(dto.encounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + dto.patientId(),
                        "painAssessment",
                        "encounter.notfound"
                ));

        try {
            resetIsActiveForEncounterToday(dto.encounterId());

            PainAssessment entity = PainAssessment.builder()
                    .patient(patient)
                    .encounterId(encounter.getId())
                    .painDegree(calculatePainDegree(dto.painLevel()))
                    .painLevel(dto.painLevel())
                    .painPattern(dto.painPattern())
                    .painDescription(dto.painDescription())
                    .isActive(true)
                    .build();

            PainAssessment saved = painAssessmentRepository.saveAndFlush(entity);


            notifyDepartmentUsersForSeverePain(saved, patient, encounter);

            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    public Optional<PainAssessment> update(Long id, PainAssessmentUpdateDTO dto) {
        Long targetId = id != null ? id : dto.id();
        LOG.info("[UPDATE] PainAssessment id={} payload={}", targetId, dto);

        return painAssessmentRepository.findById(targetId).map(entity -> {

            Patient patient = patientRepository.findById(dto.patientId())
                    .orElseThrow(() -> new NotFoundAlertException(
                            "Patient not found with id " + dto.patientId(),
                            "painAssessment",
                            "patient.notfound"
                    ));
            PatientEncounter encounter = patientEncounterRepository.findById(dto.encounterId())
                    .orElseThrow(() -> new NotFoundAlertException(
                            "Encounter not found with id " + dto.encounterId(),
                            "painAssessment",
                            "encounter.notfound"
                    ));
            entity.setPatient(patient);
            entity.setEncounterId(encounter.getId());
            entity.setPainDegree(calculatePainDegree(dto.painLevel()));
            entity.setPainLevel(dto.painLevel());
            entity.setPainPattern(dto.painPattern());
            entity.setPainDescription(dto.painDescription());
            entity.setIsActive(dto.isActive());

            try {
                PainAssessment saved = painAssessmentRepository.saveAndFlush(entity);

                notifyDepartmentUsersForSeverePain(saved, patient, encounter);

                return saved;
            } catch (DataIntegrityViolationException | JpaSystemException ex) {
                throw handleConstraintViolation(ex);
            }
        });
    }

    @Transactional(readOnly = true)
    public Optional<PainAssessment> findLatestByEncounterId(Long encounterId) {
        LOG.debug("[FIND_LATEST_BY_ENCOUNTER] encounterId={}", encounterId);
        return painAssessmentRepository.findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounterId);
    }

    private void resetIsActiveForEncounterToday(Long encounterId) {

        Instant now = Instant.now();
        Instant dayStart = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant dayEnd = dayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);

        LOG.debug(
                "[RESET ACTIVE] Setting latest PainAssessment isActive=false for today, encounterId={}",
                encounterId
        );

        painAssessmentRepository
                .findFirstByEncounterIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateDesc(
                        encounterId,
                        dayStart,
                        dayEnd
                )
                .ifPresentOrElse(painAssessment -> {
                    painAssessment.setIsActive(false);
                    painAssessmentRepository.flush();
                    LOG.debug(
                            "[RESET ACTIVE] Reset done. painAssessmentId={} encounterId={}",
                            painAssessment.getId(),
                            encounterId
                    );
                }, () -> LOG.debug(
                        "[RESET ACTIVE] No active PainAssessment found to reset"
                ));
    }


    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] PainAssessment constraint violated rootMessage={}", message, exception);

        if (messageLower.contains("fk_pain_assessment_patient")) {
            return new BadRequestAlertException("Invalid patient id.", "painAssessment", "patient.invalid");
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving pain assessment.",
                "painAssessment",
                "db.constraint"
        );
    }

    private Severity calculatePainDegree(PainLevel painLevel) {
        if (painLevel == null) {
            return null;
        }

        return switch (painLevel) {
            case LEVEL_0, LEVEL_1, LEVEL_2, LEVEL_3 -> Severity.MILD_MINOR;
            case LEVEL_4, LEVEL_5, LEVEL_6, LEVEL_7 -> Severity.MODERATE;
            case LEVEL_8, LEVEL_9, LEVEL_10 -> Severity.SEVERE;
        };
    }

    private void notifyDepartmentUsersForSeverePain(PainAssessment painAssessment, Patient patient, PatientEncounter encounter) {
        if (painAssessment == null || patient == null || encounter == null) {
            return;
        }

        if (!isSeverePain(painAssessment.getPainDegree())) {
            return;
        }

        Long departmentId = encounter.getDepartmentId();

        if (departmentId == null) {
            LOG.warn("Skip severe pain notification because encounter department is missing. painAssessmentId={}, encounterId={}", painAssessment.getId(), encounter.getId());
            return;
        }

        List<NotificationResolvedRecipientDTO> departmentUsers = buildDepartmentUserRecipients(departmentId);

        if (departmentUsers.isEmpty()) {
            LOG.warn("Skip severe pain notification because no department users found. painAssessmentId={}, departmentId={}", painAssessment.getId(), departmentId);
            return;
        }

        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();
        recipientsByRule.put("DEPARTMENT_USERS", departmentUsers);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("patientId", patient.getId());
        data.put("patientName", getPatientName(patient));
        data.put("encounterId", encounter.getId());
        data.put("departmentId", departmentId);
        data.put("painAssessmentId", painAssessment.getId());
        data.put("painLevel", painAssessment.getPainLevel());
        data.put("painDegree", painAssessment.getPainDegree() != null ? painAssessment.getPainDegree().toString() : "");
        data.put("painPattern", painAssessment.getPainPattern() != null ? painAssessment.getPainPattern().toString() : "");
        data.put("painDescription", painAssessment.getPainDescription() != null ? painAssessment.getPainDescription() : "");

        NotificationCreateDTO dto = new NotificationCreateDTO(null, "PAIN_LEVEL_SEVERE", "en", null, recipientsByRule, data, "PAIN_ASSESSMENT", painAssessment.getId());

        try {
            LOG.debug(
                    "Creating severe pain in-app notification. painAssessmentId={}, patientId={}, departmentId={}, recipientsByRule={}",
                    painAssessment.getId(),
                    patient.getId(),
                    departmentId,
                    recipientsByRule
            );

            notificationClient.createNotification(dto);
        } catch (Exception e) {
            LOG.warn("Failed to create severe pain in-app notification. painAssessmentId={}, error={}", painAssessment.getId(), e.getMessage());
        }
    }

    private boolean isSeverePain(Severity painDegree) {
        if (painDegree == null) {
            return false;
        }

        return Severity.SEVERE == painDegree;
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
                        .recipientData(Map.of(
                                "departmentId", departmentId
                        ))
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
