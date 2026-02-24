package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.AdditionalMeasurements;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.AdditionalMeasurementsRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.additionalMeasurements.AdditionalMeasurementsGeriatricCreateDTO;
import com.dazzle.asklepios.service.dto.additionalMeasurements.AdditionalMeasurementsGeriatricUpdateDTO;
import com.dazzle.asklepios.service.dto.additionalMeasurements.AdditionalMeasurementsInfantCreateDTO;
import com.dazzle.asklepios.service.dto.additionalMeasurements.AdditionalMeasurementsInfantUpdateDTO;
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
import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class AdditionalMeasurementsService {

    private static final Logger LOG = LoggerFactory.getLogger(AdditionalMeasurementsService.class);

    private static final String ENTITY_NAME = "additionalMeasurements";

    private final AdditionalMeasurementsRepository additionalMeasurementsRepository;
    private final PatientRepository patientRepository;


    public AdditionalMeasurements createInfant(AdditionalMeasurementsInfantCreateDTO dto) {
        LOG.info("[CREATE_INFANT] AdditionalMeasurements payload={}", dto);

        Patient patient = loadPatient(dto.patientId());

        try {
            resetIsActiveForEncounterToday(dto.encounterId());

            AdditionalMeasurements entity = AdditionalMeasurements.builder()
                    .patient(patient)
                    .encounterId(dto.encounterId())
                    .ageGroup(dto.ageGroup())
                    .hearingTest(dto.hearingTest())
                    .dehydration(dto.dehydration())
                    .nasalFlaring(dto.nasalFlaring())
                    .responseToLight(dto.responseToLight())
                    .pupilResponse(dto.pupilResponse())
                    .abilityToFollowTarget(dto.abilityToFollowTarget())
                    .colorTesting(dto.colorTesting())
                    .isActive(true)
                    .build();

            return additionalMeasurementsRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    public Optional<AdditionalMeasurements> updateInfant(Long id, AdditionalMeasurementsInfantUpdateDTO dto) {
        Long targetId = id != null ? id : dto.id();
        LOG.info("[UPDATE_INFANT] AdditionalMeasurements id={} payload={}", targetId, dto);

        return additionalMeasurementsRepository.findById(targetId).map(entity -> {
            Patient patient = loadPatient(dto.patientId());

            entity.setPatient(patient);
            entity.setEncounterId(dto.encounterId());
            entity.setAgeGroup(dto.ageGroup());
            entity.setHearingTest(dto.hearingTest());
            entity.setDehydration(dto.dehydration());
            entity.setNasalFlaring(dto.nasalFlaring());
            entity.setResponseToLight(dto.responseToLight());
            entity.setPupilResponse(dto.pupilResponse());
            entity.setAbilityToFollowTarget(dto.abilityToFollowTarget());
            entity.setColorTesting(dto.colorTesting());

            entity.setIsActive(dto.isActive());

            try {
                return additionalMeasurementsRepository.saveAndFlush(entity);
            } catch (DataIntegrityViolationException | JpaSystemException ex) {
                throw handleConstraintViolation(ex);
            }
        });
    }

    public AdditionalMeasurements createGeriatric(AdditionalMeasurementsGeriatricCreateDTO dto) {
        LOG.info("[CREATE_GERIATRIC] AdditionalMeasurements payload={}", dto);

        Patient patient = loadPatient(dto.patientId());

        try {
            resetIsActiveForEncounterToday(dto.encounterId());

            AdditionalMeasurements entity = AdditionalMeasurements.builder()
                    .patient(patient)
                    .encounterId(dto.encounterId())
                    .ageGroup(dto.ageGroup())
                    .fallRisk(dto.fallRisk())
                    .visionProblemsAffectingFunction(dto.visionProblemsAffectingFunction())
                    .hearingProblemsAffectingFunction(dto.hearingProblemsAffectingFunction())
                    .details(dto.details())
                    .actionToTake(dto.actionToTake())
                    .isActive(true)
                    .build();

            return additionalMeasurementsRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    public Optional<AdditionalMeasurements> updateGeriatric(Long id, AdditionalMeasurementsGeriatricUpdateDTO dto) {
        Long targetId = id != null ? id : dto.id();
        LOG.info("[UPDATE_GERIATRIC] AdditionalMeasurements id={} payload={}", targetId, dto);


        return additionalMeasurementsRepository.findById(targetId).map(entity -> {
            Patient patient = loadPatient(dto.patientId());

            entity.setPatient(patient);
            entity.setEncounterId(dto.encounterId());
            entity.setAgeGroup(dto.ageGroup());
            entity.setFallRisk(dto.fallRisk());
            entity.setVisionProblemsAffectingFunction(dto.visionProblemsAffectingFunction());
            entity.setHearingProblemsAffectingFunction(dto.hearingProblemsAffectingFunction());
            entity.setDetails(dto.details());
            entity.setActionToTake(dto.actionToTake());

            entity.setIsActive(dto.isActive());

            try {
                return additionalMeasurementsRepository.saveAndFlush(entity);
            } catch (DataIntegrityViolationException | JpaSystemException ex) {
                throw handleConstraintViolation(ex);
            }
        });
    }

    @Transactional(readOnly = true)
    public Optional<AdditionalMeasurements> findLatestByEncounterId(Long encounterId) {
        LOG.debug("[FIND_LATEST_BY_ENCOUNTER] encounterId={}", encounterId);
        return additionalMeasurementsRepository.findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounterId);
    }

    private Patient loadPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        ENTITY_NAME,
                        "patient.notfound"
                ));
    }

    private void resetIsActiveForEncounterToday(Long encounterId) {

        Instant now = Instant.now();
        Instant dayStart = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant dayEnd = dayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);

        LOG.debug(
                "[RESET ACTIVE] Setting latest isActive=false for today, encounterId={}",
                encounterId
        );

        additionalMeasurementsRepository
                .findFirstByEncounterIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateDesc(
                        encounterId,
                        dayStart,
                        dayEnd
                )
                .ifPresentOrElse(additionalMeasurements -> {
                    additionalMeasurements.setIsActive(false);
                    additionalMeasurementsRepository.flush();
                    LOG.debug(
                            "[RESET ACTIVE] Reset done. additionalMeasurementsId={} encounterId={}",
                            additionalMeasurements.getId(),
                            encounterId
                    );
                }, () -> LOG.debug(
                        "[RESET ACTIVE] No active AdditionalMeasurements found to reset"
                ));
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] AdditionalMeasurements constraint violated rootMessage={}", message, exception);

        if (messageLower.contains("fk_additional_measurements_patient")) {
            return new BadRequestAlertException("Invalid patient id.", ENTITY_NAME, "patient.invalid");
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving additional measurements.",
                ENTITY_NAME,
                "db.constraint"
        );
    }
}
