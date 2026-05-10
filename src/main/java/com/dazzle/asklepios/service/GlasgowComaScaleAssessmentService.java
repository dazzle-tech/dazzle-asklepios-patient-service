package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.GlasgowComaScaleAssessment;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.repository.GlasgowComaScaleAssessmentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
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
                .orElseThrow(() -> {
                    LOG.warn("[CREATE] GlasgowComaScaleAssessment rejected: encounter not found encounterId={}",
                            createDTO.encounterId());
                    return new NotFoundAlertException(
                            "Encounter not found with id " + createDTO.encounterId(),
                            ENTITY_NAME,
                            "encounter.notfound"
                    );
                });

        Patient patient = patientRepository.findById(createDTO.patientId())
                .orElseThrow(() -> {
                    LOG.warn("[CREATE] GlasgowComaScaleAssessment rejected: patient not found patientId={}",
                            createDTO.patientId());
                    return new NotFoundAlertException(
                            "Patient not found with id " + createDTO.patientId(),
                            ENTITY_NAME,
                            "patient.notfound"
                    );
                });

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
            GlasgowComaScaleAssessment saved = glasgowComaScaleAssessmentRepository.saveAndFlush(entity);

            LOG.info("[CREATE] GlasgowComaScaleAssessment success id={} encounterId={} patientId={} totalScore={}",
                    saved.getId(), createDTO.encounterId(), createDTO.patientId(), saved.getTotalScore());

            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            LOG.warn("[CREATE] GlasgowComaScaleAssessment failed (constraint) payload={}", createDTO, exception);
            throw handleConstraintViolation(exception);
        } catch (RuntimeException exception) {
            LOG.error("[CREATE] GlasgowComaScaleAssessment failed (unexpected) payload={}", createDTO, exception);
            throw exception;
        }
    }

    public GlasgowComaScaleAssessment update(Long id, GlasgowComaScaleAssessmentUpdateDTO updateDTO) {
        LOG.info("[UPDATE] GlasgowComaScaleAssessment id={} payload={}", id, updateDTO);

        if (!id.equals(updateDTO.id())) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id.",
                    ENTITY_NAME,
                    "id.mismatch"
            );
        }

        GlasgowComaScaleAssessment existing = glasgowComaScaleAssessmentRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.warn("[UPDATE] GlasgowComaScaleAssessment rejected: not found id={}", id);
                    return new NotFoundAlertException(
                            "GlasgowComaScaleAssessment not found with id " + id,
                            ENTITY_NAME,
                            "id.notfound"
                    );
                });

        PatientEncounter encounter = patientEncounterRepository.findById(updateDTO.encounterId())
                .orElseThrow(() -> {
                    LOG.warn("[UPDATE] GlasgowComaScaleAssessment rejected: encounter not found encounterId={}",
                            updateDTO.encounterId());
                    return new NotFoundAlertException(
                            "Encounter not found with id " + updateDTO.encounterId(),
                            ENTITY_NAME,
                            "encounter.notfound"
                    );
                });

        Patient patient = patientRepository.findById(updateDTO.patientId())
                .orElseThrow(() -> {
                    LOG.warn("[UPDATE] GlasgowComaScaleAssessment rejected: patient not found patientId={}",
                            updateDTO.patientId());
                    return new NotFoundAlertException(
                            "Patient not found with id " + updateDTO.patientId(),
                            ENTITY_NAME,
                            "patient.notfound"
                    );
                });

        validatePatientMatchesEncounter(encounter, patient);

        Integer eyeScore = updateDTO.eyeOpening().getScore();
        Integer verbalScore = updateDTO.verbalResponse().getScore();
        Integer motorScore = updateDTO.motorResponse().getScore();
        Integer totalScore = calculateTotalScore(eyeScore, verbalScore, motorScore);

        existing.setEncounter(encounter);
        existing.setPatient(patient);
        existing.setEyeOpening(updateDTO.eyeOpening());
        existing.setEyeOpeningScore(eyeScore);
        existing.setVerbalResponse(updateDTO.verbalResponse());
        existing.setVerbalResponseScore(verbalScore);
        existing.setMotorResponse(updateDTO.motorResponse());
        existing.setMotorResponseScore(motorScore);
        existing.setTotalScore(totalScore);
        existing.setScoreInterpretation(resolveScoreInterpretation(totalScore));

        try {
            GlasgowComaScaleAssessment saved = glasgowComaScaleAssessmentRepository.saveAndFlush(existing);

            LOG.info("[UPDATE] GlasgowComaScaleAssessment success id={} encounterId={} patientId={} totalScore={}",
                    saved.getId(), updateDTO.encounterId(), updateDTO.patientId(), saved.getTotalScore());

            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            LOG.warn("[UPDATE] GlasgowComaScaleAssessment failed (constraint) id={} payload={}",
                    id, updateDTO, exception);
            throw handleConstraintViolation(exception);
        } catch (RuntimeException exception) {
            LOG.error("[UPDATE] GlasgowComaScaleAssessment failed (unexpected) id={} payload={}",
                    id, updateDTO, exception);
            throw exception;
        }
    }

    public void delete(Long id) {
        LOG.info("[DELETE] GlasgowComaScaleAssessment id={}", id);

        GlasgowComaScaleAssessment existing = glasgowComaScaleAssessmentRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.warn("[DELETE] GlasgowComaScaleAssessment rejected: not found id={}", id);
                    return new NotFoundAlertException(
                            "GlasgowComaScaleAssessment not found with id " + id,
                            ENTITY_NAME,
                            "id.notfound"
                    );
                });

        try {
            glasgowComaScaleAssessmentRepository.delete(existing);
            glasgowComaScaleAssessmentRepository.flush();

            LOG.info("[DELETE] GlasgowComaScaleAssessment success id={}", id);
        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            LOG.warn("[DELETE] GlasgowComaScaleAssessment failed (constraint) id={}", id, exception);
            throw handleConstraintViolation(exception);
        } catch (RuntimeException exception) {
            LOG.error("[DELETE] GlasgowComaScaleAssessment failed (unexpected) id={}", id, exception);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public GlasgowComaScaleAssessment getById(Long id) {
        LOG.debug("[GET_BY_ID] GlasgowComaScaleAssessment id={}", id);

        return glasgowComaScaleAssessmentRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.warn("[GET_BY_ID] GlasgowComaScaleAssessment not found id={}", id);
                    return new NotFoundAlertException(
                            "GlasgowComaScaleAssessment not found with id " + id,
                            ENTITY_NAME,
                            "id.notfound"
                    );
                });
    }

    @Transactional(readOnly = true)
    public Page<GlasgowComaScaleAssessment> getAllByEncounterId(Long encounterId, Pageable pageable) {
        LOG.debug("[GET_LIST_BY_ENCOUNTER] encounterId={} pageable={}", encounterId, pageable);

        if (!patientEncounterRepository.existsById(encounterId)) {
            LOG.warn("[GET_LIST_BY_ENCOUNTER] rejected: encounter not found encounterId={}", encounterId);
            throw new NotFoundAlertException(
                    "Encounter not found with id " + encounterId,
                    ENTITY_NAME,
                    "encounter.notfound"
            );
        }

        return glasgowComaScaleAssessmentRepository.findAllByEncounter_Id(encounterId, pageable);
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

    private String resolveScoreInterpretation(Integer totalScore) {
        if (totalScore >= 13 && totalScore <= 15) {
            return "Mild traumatic brain injury";
        }

        if (totalScore >= 9 && totalScore <= 12) {
            return "Moderate traumatic brain injury";
        }

        if (totalScore >= 3 && totalScore <= 8) {
            return "Severe traumatic brain injury (coma)";
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