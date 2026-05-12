package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.GlasgowComaScaleAssessment;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.GCSScoreInterpretation;
import com.dazzle.asklepios.repository.GlasgowComaScaleAssessmentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.glasgowComaScaleAssessment.GlasgowComaScaleAssessmentCancelDTO;
import com.dazzle.asklepios.service.dto.glasgowComaScaleAssessment.GlasgowComaScaleAssessmentCreateDTO;
import com.dazzle.asklepios.service.dto.glasgowComaScaleAssessment.GlasgowComaScaleAssessmentUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class GlasgowComaScaleAssessmentService {

    private static final Logger LOG = LoggerFactory.getLogger(GlasgowComaScaleAssessmentService.class);

    private static final String ENTITY_NAME = "glasgowComaScaleAssessment";

    private final GlasgowComaScaleAssessmentRepository glasgowComaScaleAssessmentRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientRepository patientRepository;

    public GlasgowComaScaleAssessment create(GlasgowComaScaleAssessmentCreateDTO createDTO) {
        LOG.info("[CREATE] GlasgowComaScaleAssessment payload={}", createDTO);

        PatientEncounter encounter = patientEncounterRepository.findById(createDTO.encounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + createDTO.encounterId(),
                        ENTITY_NAME,
                        "encounter.notfound"
                ));

        Patient patient = patientRepository.findById(createDTO.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + createDTO.patientId(),
                        ENTITY_NAME,
                        "patient.notfound"
                ));

        validatePatientMatchesEncounter(encounter, patient);

        Integer eyeScore = createDTO.eyeOpening().getScore();
        Integer verbalScore = createDTO.verbalResponse().getScore();
        Integer motorScore = createDTO.motorResponse().getScore();
        Integer totalScore = calculateTotalScore(eyeScore, verbalScore, motorScore);

        GlasgowComaScaleAssessment entity = GlasgowComaScaleAssessment.builder()
                .encounter(encounter)
                .patient(patient)
                .eyeOpening(createDTO.eyeOpening())
                .eyeOpeningScore(eyeScore)
                .verbalResponse(createDTO.verbalResponse())
                .verbalResponseScore(verbalScore)
                .motorResponse(createDTO.motorResponse())
                .motorResponseScore(motorScore)
                .totalScore(totalScore)
                .scoreInterpretation(resolveScoreInterpretation(totalScore))
                .build();

        try {
            return glasgowComaScaleAssessmentRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            throw handleConstraintViolation(exception);
        }
    }

    public GlasgowComaScaleAssessment update(Long id, GlasgowComaScaleAssessmentUpdateDTO updateDTO) {
        LOG.info("[UPDATE] GlasgowComaScaleAssessment id={} payload={}", id, updateDTO);

        GlasgowComaScaleAssessment existing = glasgowComaScaleAssessmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "GlasgowComaScaleAssessment not found with id " + id,
                        ENTITY_NAME,
                        "id.notfound"
                ));


        Integer eyeScore = updateDTO.eyeOpening().getScore();
        Integer verbalScore = updateDTO.verbalResponse().getScore();
        Integer motorScore = updateDTO.motorResponse().getScore();
        Integer totalScore = calculateTotalScore(eyeScore, verbalScore, motorScore);

        existing.setEyeOpening(updateDTO.eyeOpening());
        existing.setEyeOpeningScore(eyeScore);
        existing.setVerbalResponse(updateDTO.verbalResponse());
        existing.setVerbalResponseScore(verbalScore);
        existing.setMotorResponse(updateDTO.motorResponse());
        existing.setMotorResponseScore(motorScore);
        existing.setTotalScore(totalScore);
        existing.setScoreInterpretation(resolveScoreInterpretation(totalScore));

        try {
            return glasgowComaScaleAssessmentRepository.saveAndFlush(existing);
        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            throw handleConstraintViolation(exception);
        }
    }

    public GlasgowComaScaleAssessment cancel(GlasgowComaScaleAssessmentCancelDTO cancelDTO) {
        String currentUser = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "Current user not found",
                        ENTITY_NAME,
                        "user.notfound"
                ));

        GlasgowComaScaleAssessment existing = glasgowComaScaleAssessmentRepository.findById(cancelDTO.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "GlasgowComaScaleAssessment not found with id " + cancelDTO.id(),
                        ENTITY_NAME,
                        "id.notfound"
                ));

        existing.setCancelledAt(LocalDateTime.now());
        existing.setCancelledBy(currentUser);
        existing.setCancellationReason(cancelDTO.cancellationReason());

        try {
            return glasgowComaScaleAssessmentRepository.saveAndFlush(existing);
        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            throw handleConstraintViolation(exception);
        }
    }

    @Transactional(readOnly = true)
    public GlasgowComaScaleAssessment getById(Long id) {
        return glasgowComaScaleAssessmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "GlasgowComaScaleAssessment not found with id " + id,
                        ENTITY_NAME,
                        "id.notfound"
                ));
    }

    @Transactional(readOnly = true)
    public Page<GlasgowComaScaleAssessment> getAllByEncounterId(Long encounterId, Pageable pageable) {
        if (!patientEncounterRepository.existsById(encounterId)) {
            throw new NotFoundAlertException(
                    "Encounter not found with id " + encounterId,
                    ENTITY_NAME,
                    "encounter.notfound"
            );
        }

        return glasgowComaScaleAssessmentRepository.findAllByEncounter_Id(encounterId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<GlasgowComaScaleAssessment> getActiveByEncounterId(Long encounterId, Pageable pageable) {
        if (!patientEncounterRepository.existsById(encounterId)) {
            throw new NotFoundAlertException(
                    "Encounter not found with id " + encounterId,
                    ENTITY_NAME,
                    "encounter.notfound"
            );
        }

        return glasgowComaScaleAssessmentRepository.findAllByEncounter_IdAndCancelledAtIsNull(encounterId, pageable);
    }

    private void validatePatientMatchesEncounter(PatientEncounter encounter, Patient patient) {
        if (!encounter.getPatient().getId().equals(patient.getId())) {
            throw new BadRequestAlertException(
                    "The provided patient does not belong to the provided encounter.",
                    ENTITY_NAME,
                    "patient.encounter.mismatch"
            );
        }
    }

    private Integer calculateTotalScore(Integer eyeScore, Integer verbalScore, Integer motorScore) {
        return eyeScore + verbalScore + motorScore;
    }

    private GCSScoreInterpretation resolveScoreInterpretation(Integer totalScore) {

        if (totalScore >= 13 && totalScore <= 15) {
            return GCSScoreInterpretation.MILD_TRAUMATIC_BRAIN_INJURY;
        }

        if (totalScore >= 9 && totalScore <= 12) {
            return GCSScoreInterpretation.MODERATE_TRAUMATIC_BRAIN_INJURY;
        }

        if (totalScore >= 3 && totalScore <= 8) {
            return GCSScoreInterpretation.SEVERE_TRAUMATIC_BRAIN_INJURY_COMA;
        }

        throw new BadRequestAlertException(
                "Invalid GCS total score.",
                ENTITY_NAME,
                "gcs.score.invalid"
        );
    }
    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable rootCause = getRootCause(exception);
        String rootMessage = rootCause != null ? rootCause.getMessage() : exception.getMessage();
        String rootMessageLower = rootMessage != null ? rootMessage.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] GlasgowComaScaleAssessment constraint violated rootMessage={}",
                rootMessage, exception);

        if (rootMessageLower.contains("ck_gcs_cancelled_fields")) {
            return new BadRequestAlertException(
                    "CancelledBy and cancellationReason are required when cancelling Glasgow Coma Scale assessment.",
                    ENTITY_NAME,
                    "gcs.cancel.invalid"
            );
        }

        if (rootMessageLower.contains("fk_triage_gcs_patient")) {
            return new BadRequestAlertException(
                    "Invalid patient id.",
                    ENTITY_NAME,
                    "patient.invalid"
            );
        }

        if (rootMessageLower.contains("fk_triage_gcs_encounter")) {
            return new BadRequestAlertException(
                    "Invalid encounter id.",
                    ENTITY_NAME,
                    "encounter.invalid"
            );
        }

        if (rootMessageLower.contains("fk") && rootMessageLower.contains("patient")) {
            return new NotFoundAlertException(
                    "Patient not found.",
                    ENTITY_NAME,
                    "patient.notfound"
            );
        }

        if (rootMessageLower.contains("fk") && rootMessageLower.contains("encounter")) {
            return new NotFoundAlertException(
                    "Encounter not found.",
                    ENTITY_NAME,
                    "encounter.notfound"
            );
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving Glasgow Coma Scale assessment.",
                ENTITY_NAME,
                "db.constraint"
        );
    }
}