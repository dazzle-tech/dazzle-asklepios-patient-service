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
            // ✅ FIX: save the deactivated record first, flush it, THEN create the new one
            // This avoids any constraint timing issue between the two operations
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
                    // ✅ FIX: always force isActive=true on create regardless of DTO value
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
            // ✅ FIX: same as infant — reset first, flush, then create
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
                    // ✅ FIX: always force isActive=true on create regardless of DTO value
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
        return additionalMeasurementsRepository
                .findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounterId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Patient loadPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        ENTITY_NAME,
                        "patient.notfound"
                ));
    }

    /**
     * Deactivates the most recent active record for today's encounter BEFORE
     * inserting a new one. Uses saveAndFlush (not just flush) so the UPDATE is
     * committed to the DB connection before the INSERT, preventing any unique
     * or check constraint race between the two rows.
     */
    private void resetIsActiveForEncounterToday(Long encounterId) {
        Instant now = Instant.now();
        Instant dayStart = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant dayEnd = dayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);

        LOG.debug("[RESET_ACTIVE] Setting latest isActive=false for today, encounterId={}", encounterId);

        additionalMeasurementsRepository
                .findFirstByEncounterIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateDesc(
                        encounterId, dayStart, dayEnd)
                .ifPresentOrElse(existing -> {
                    existing.setIsActive(false);
                    // ✅ FIX: saveAndFlush instead of just flush()
                    // flush() alone only flushes the dirty state to the JDBC batch;
                    // saveAndFlush guarantees the UPDATE reaches the DB before the next INSERT
                    additionalMeasurementsRepository.saveAndFlush(existing);
                    LOG.debug("[RESET_ACTIVE] Done. deactivatedId={} encounterId={}",
                            existing.getId(), encounterId);
                }, () -> LOG.debug("[RESET_ACTIVE] No active record found to deactivate"));
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.error("[DB_CONSTRAINT] ROOT CAUSE: {}", message, exception);

        if (messageLower.contains("fk_additional_measurements_patient")) {
            return new BadRequestAlertException("Invalid patient id.", ENTITY_NAME, "patient.invalid");
        }

        if (messageLower.contains("fk_additional_measurements_encounter")) {
            return new BadRequestAlertException("Invalid encounter id.", ENTITY_NAME, "encounter.invalid");
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving additional measurements.",
                ENTITY_NAME,
                "db.constraint"
        );
    }

}