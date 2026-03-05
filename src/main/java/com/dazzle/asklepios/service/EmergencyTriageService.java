package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.EmergencyTriage;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.AVPUScale;
import com.dazzle.asklepios.domain.enumeration.EmergencyLevel;
import com.dazzle.asklepios.domain.enumeration.PainLevel;
import com.dazzle.asklepios.domain.enumeration.YesNoQuestion;
import com.dazzle.asklepios.repository.EmergencyTriageRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.emergencyTriage.EmergencyTriageCreateDTO;
import com.dazzle.asklepios.service.dto.emergencyTriage.EmergencyTriageDestinationUpdateDTO;
import com.dazzle.asklepios.service.dto.emergencyTriage.EmergencyTriageLevelAssessmentUpdateDTO;
import com.dazzle.asklepios.service.dto.emergencyTriage.EmergencyTriageUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional
public class EmergencyTriageService {

    private static final Logger LOG = LoggerFactory.getLogger(EmergencyTriageService.class);
    private static final String ENTITY_NAME = "EmergencyTriage";

    private final EmergencyTriageRepository emergencyTriageRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;

    public EmergencyTriageService(EmergencyTriageRepository emergencyTriageRepository, PatientRepository patientRepository, PatientEncounterRepository patientEncounterRepository) {
        this.emergencyTriageRepository = emergencyTriageRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
    }

    /**
     * Flow requirement:
     * - If there is already a record for this encounter -> return it (do NOT create new one)
     * - Otherwise create once and return it
     */
    public EmergencyTriage createOrGetByEncounter(EmergencyTriageCreateDTO dto) {
        LOG.debug("createOrGetByEncounter EmergencyTriage payload={}", dto);

        return emergencyTriageRepository
                .findTopByEncounter_IdOrderByCreatedDateDesc(dto.encounterId())
                .orElseGet(() -> {
                    Patient patient = getPatient(dto.patientId());
                    PatientEncounter encounter = getEncounter(dto.encounterId());

                    EmergencyTriage entity = EmergencyTriage.builder()
                            .patient(patient)
                            .encounter(encounter)
                            .build();

                    EmergencyTriage saved = emergencyTriageRepository.save(entity);
                    LOG.debug("createOrGetByEncounter: created id={}", saved.getId());
                    return saved;
                });
    }

    @Transactional(readOnly = true)
    public Optional<EmergencyTriage> getLatestByEncounterId(Long encounterId) {
        LOG.debug("get latest EmergencyTriage by encounterId={}", encounterId);
        return emergencyTriageRepository.findTopByEncounter_IdOrderByCreatedDateDesc(encounterId);
    }

    /**
     * Section update: eye/pupil/HPI
     */
    public EmergencyTriage updateEyeAssessment(EmergencyTriageUpdateDTO dto) {
        LOG.debug("updateEyeAssessment EmergencyTriage payload={}", dto);
        EmergencyTriage entity = getRequired(dto.id());

        if (dto.rightEyeLightResponse() != null) entity.setRightEyeLightResponse(dto.rightEyeLightResponse());
        if (dto.rightEyePupilSize() != null) entity.setRightEyePupilSize(dto.rightEyePupilSize());
        if (dto.leftEyeLightResponse() != null) entity.setLeftEyeLightResponse(dto.leftEyeLightResponse());
        if (dto.leftEyePupilSize() != null) entity.setLeftEyePupilSize(dto.leftEyePupilSize());
        if (dto.hpiAdditionalNotes() != null) entity.setHpiAdditionalNotes(dto.hpiAdditionalNotes());

        // does not affect the matrix, but safe to re-calculate (no change if inputs missing)
        recalculateAndSetEmergencyLevel(entity);

        EmergencyTriage saved = emergencyTriageRepository.save(entity);
        LOG.debug("updateEyeAssessment: saved id={}", saved.getId());
        return saved;
    }

    /**
     * Section update: triage level assessment + required services (matrix-driven emergencyLevel)
     */
    public EmergencyTriage updateLevelAssessment(EmergencyTriageLevelAssessmentUpdateDTO dto) {
        LOG.debug("updateLevelAssessment EmergencyTriage payload={}", dto);
        EmergencyTriage entity = getRequired(dto.id());

        if (dto.lifeSaving() != null) entity.setLifeSaving(dto.lifeSaving());
        if (dto.unresponsive() != null) entity.setUnresponsive(dto.unresponsive());
        if (dto.highRisk() != null) entity.setHighRisk(dto.highRisk());
        if (dto.avpuScale() != null) entity.setAvpuScale(dto.avpuScale());
        if (dto.painScore() != null) entity.setPainScore(dto.painScore());

        if (dto.labsRequired() != null) entity.setLabsRequired(dto.labsRequired());
        if (dto.imagingRequired() != null) entity.setImagingRequired(dto.imagingRequired());
        if (dto.ivFluidsRequired() != null) entity.setIvFluidsRequired(dto.ivFluidsRequired());
        if (dto.medicationRequired() != null) entity.setMedicationRequired(dto.medicationRequired());
        if (dto.ecgRequired() != null) entity.setEcgRequired(dto.ecgRequired());
        if (dto.consultationRequired() != null) entity.setConsultationRequired(dto.consultationRequired());

        recalculateAndSetEmergencyLevel(entity);

        EmergencyTriage saved = emergencyTriageRepository.save(entity);
        LOG.debug("updateLevelAssessment: saved id={}", saved.getId());
        return saved;
    }

    /**
     * Section update: destination only and set completed date
     */
    public EmergencyTriage updateDestination(EmergencyTriageDestinationUpdateDTO dto) {
        LOG.debug("updateDestination EmergencyTriage payload={}", dto);
        EmergencyTriage entity = getRequired(dto.id());

        if (dto.destination() != null) entity.setDestination(dto.destination());
        entity.setCompletedDate(Instant.now());
        EmergencyTriage saved = emergencyTriageRepository.save(entity);
        LOG.debug("updateDestination: saved id={}", saved.getId());
        return saved;
    }

    @Transactional
    public void hardDelete(Long id) {
        LOG.debug("hard delete EmergencyTriage id={}", id);
        if (!emergencyTriageRepository.existsById(id)) {
            throw new NotFoundAlertException("EmergencyTriage not found: " + id, ENTITY_NAME, "notfound");
        }
        emergencyTriageRepository.deleteById(id);
    }

    // -----------------------
    // Matrix Emergency Level calculation
    // -----------------------

    private void recalculateAndSetEmergencyLevel(EmergencyTriage triage) {
        EmergencyLevel computed = calculateEmergencyLevel(triage);
        if (computed != null && computed != triage.getEmergencyLevel()) {
            triage.setEmergencyLevel(computed);
        }
    }

    /**
     * Mirrors the React matrix logic (enum-based):
     * 1) CRITICAL if lifeSaving==YES OR unresponsive==YES
     * 2) SERIOUS if highRisk==YES OR avpuScale hits critical key OR painScore is critical
     * 3) Otherwise if (highRisk,avpuScale,painScore) are all set and none are critical:
     * - HIGH if >=2 required services are YES
     * - MEDIUM if exactly 1 required service is YES
     * - LOW if 0 required services are YES
     * <p>
     * If there isn't enough information yet, returns null (keeps current emergencyLevel).
     */
    private EmergencyLevel calculateEmergencyLevel(EmergencyTriage emergencyTriage) {
        if (emergencyTriage.getLifeSaving() == YesNoQuestion.YES || emergencyTriage.getUnresponsive() == YesNoQuestion.YES) {
            return EmergencyLevel.RESUSCITATION;
        }

        if (emergencyTriage.getHighRisk() == YesNoQuestion.YES || isSeriousAvpu(emergencyTriage.getAvpuScale()) || isCriticalPain(emergencyTriage.getPainScore())) {
            return EmergencyLevel.EMERGENT;
        }

        boolean hasCoreInputs = emergencyTriage.getHighRisk() != null && emergencyTriage.getAvpuScale() != null && emergencyTriage.getPainScore() != null;
        boolean coreInputsAreNonCritical =
                emergencyTriage.getHighRisk() != YesNoQuestion.YES && !isSeriousAvpu(emergencyTriage.getAvpuScale()) && !isCriticalPain(emergencyTriage.getPainScore());

        if (hasCoreInputs && coreInputsAreNonCritical) {
            int yesCount = countYes(emergencyTriage.getLabsRequired(), emergencyTriage.getImagingRequired()
                    , emergencyTriage.getIvFluidsRequired(), emergencyTriage.getMedicationRequired()
                    , emergencyTriage.getEcgRequired(), emergencyTriage.getConsultationRequired());

            if (yesCount >= 2) return EmergencyLevel.URGENT;
            if (yesCount == 1) return EmergencyLevel.LESS_URGENT;
            return EmergencyLevel.NON_URGENT;
        }

        return null;
    }

    private int countYes(YesNoQuestion... values) {
        int c = 0;
        if (values == null) return 0;
        for (YesNoQuestion v : values) {
            if (v == YesNoQuestion.YES) c++;
        }
        return c;
    }

    private boolean isSeriousAvpu(AVPUScale scale) {
        if (scale == null) return false;
        return scale == AVPUScale.UNRESPONSIVE;
    }

    private boolean isCriticalPain(PainLevel pain) {
        if (pain == null) return false;

        return pain == PainLevel.LEVEL_7 || pain == PainLevel.LEVEL_8 || pain == PainLevel.LEVEL_9 || pain == PainLevel.LEVEL_10;
    }

    // -----------------------
    // Helpers
    // -----------------------

    private EmergencyTriage getRequired(Long id) {
        return emergencyTriageRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundAlertException("EmergencyTriage not found: " + id, ENTITY_NAME, "notfound"));
    }

    private Patient getPatient(Long id) {
        return patientRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundAlertException("Patient not found: " + id, "Patient", "notfound"));
    }
    private PatientEncounter getEncounter(Long id) {
        return patientEncounterRepository.findById(id).orElseThrow(() -> new NotFoundAlertException("Patient Encounter not found: " + id, "PatientEncounter", "notfound"));
    }
}