package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.GeneralAssessment;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.GeneralAssessmentRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.generalAssessment.GeneralAssessmentCreateDTO;
import com.dazzle.asklepios.service.dto.generalAssessment.GeneralAssessmentUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GeneralAssessmentService {

    private static final Logger LOG = LoggerFactory.getLogger(GeneralAssessmentService.class);
    private static final String ENTITY_NAME = "GeneralAssessment";

    private final GeneralAssessmentRepository generalAssessmentRepository;
    private final PatientRepository patientRepository;

    public GeneralAssessmentService(GeneralAssessmentRepository generalAssessmentRepository, PatientRepository patientRepository) {
        this.generalAssessmentRepository = generalAssessmentRepository;
        this.patientRepository = patientRepository;
    }

    public GeneralAssessment create(GeneralAssessmentCreateDTO generalAssessmentCreateDTO) {
        LOG.debug("create general assessment {}", generalAssessmentCreateDTO);
        GeneralAssessment entity = toEntityForCreate(generalAssessmentCreateDTO);
        GeneralAssessment saved = generalAssessmentRepository.save(entity);
        LOG.debug("create: saved id={}", saved.getId());
        return saved;
    }

    public GeneralAssessment update(GeneralAssessmentUpdateDTO generalAssessmentUpdateDTO) {
        LOG.debug("update general assessment {}", generalAssessmentUpdateDTO);
        GeneralAssessment entity = getRequired(generalAssessmentUpdateDTO.id());
        applyUpdate(entity, generalAssessmentUpdateDTO);
        GeneralAssessment saved = generalAssessmentRepository.save(entity);
        LOG.debug("update: saved id={}", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public GeneralAssessment getLatestByEncounterId(Long encounterId) {
        LOG.debug("get latest general assessment by encounterId={}", encounterId);
        return generalAssessmentRepository
                .findTopByEncounterIdOrderByCreatedDateDesc(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "GeneralAssessment not found for encounter: " + encounterId,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    @Transactional(readOnly = true)
    public GeneralAssessment getLatestTriageByEncounterId(Long encounterId) {
        LOG.debug("get latest triage general assessment by encounterId={}", encounterId);
        return generalAssessmentRepository
                .findTopByEncounterIdAndIsTriageTrueOrderByCreatedDateDesc(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Triage GeneralAssessment not found for encounter: " + encounterId,
                        ENTITY_NAME,
                        "notfound"
                ));
    }

    @Transactional
    public void hardDelete(Long id) {
        LOG.debug("hard delete GeneralAssessment id={}", id);
        if (!generalAssessmentRepository.existsById(id)) {
            throw new NotFoundAlertException("GeneralAssessment not found: " + id, ENTITY_NAME, "notfound");
        }
        generalAssessmentRepository.deleteById(id);
    }
    // Helpers

    private GeneralAssessment toEntityForCreate(GeneralAssessmentCreateDTO generalAssessmentCreateDTO) {
        Patient patient = getPatient(generalAssessmentCreateDTO.patientId());

        return GeneralAssessment.builder()
                .patient(patient)
                .encounterId(generalAssessmentCreateDTO.encounterId())
                .positionStatus(generalAssessmentCreateDTO.positionStatus())
                .bodyMovements(generalAssessmentCreateDTO.bodyMovements())
                .levelOfConsciousness(generalAssessmentCreateDTO.levelOfConsciousness())
                .facialExpression(generalAssessmentCreateDTO.facialExpression())
                .speech(generalAssessmentCreateDTO.speech())
                .moodBehavior(generalAssessmentCreateDTO.moodBehavior())
                .memoryRemote(Boolean.TRUE.equals(generalAssessmentCreateDTO.memoryRemote()))
                .memoryRecent(Boolean.TRUE.equals(generalAssessmentCreateDTO.memoryRecent()))
                .signsOfAgitation(Boolean.TRUE.equals(generalAssessmentCreateDTO.signsOfAgitation()))
                .signsOfDepression(Boolean.TRUE.equals(generalAssessmentCreateDTO.signsOfDepression()))
                .signsOfSuicidalIdeation(Boolean.TRUE.equals(generalAssessmentCreateDTO.signsOfSuicidalIdeation()))
                .signsOfSubstanceUse(Boolean.TRUE.equals(generalAssessmentCreateDTO.signsOfSubstanceUse()))
                .isTriage(Boolean.TRUE.equals(generalAssessmentCreateDTO.isTriage()))
                .build();
    }

    private void applyUpdate(GeneralAssessment entity, GeneralAssessmentUpdateDTO generalAssessmentUpdateDTO) {
        if (generalAssessmentUpdateDTO.positionStatus() != null) entity.setPositionStatus(generalAssessmentUpdateDTO.positionStatus());
        if (generalAssessmentUpdateDTO.bodyMovements() != null) entity.setBodyMovements(generalAssessmentUpdateDTO.bodyMovements());
        if (generalAssessmentUpdateDTO.levelOfConsciousness() != null) entity.setLevelOfConsciousness(generalAssessmentUpdateDTO.levelOfConsciousness());
        if (generalAssessmentUpdateDTO.facialExpression() != null) entity.setFacialExpression(generalAssessmentUpdateDTO.facialExpression());
        if (generalAssessmentUpdateDTO.speech() != null) entity.setSpeech(generalAssessmentUpdateDTO.speech());
        if (generalAssessmentUpdateDTO.moodBehavior() != null) entity.setMoodBehavior(generalAssessmentUpdateDTO.moodBehavior());

        if (generalAssessmentUpdateDTO.memoryRemote() != null) entity.setMemoryRemote(generalAssessmentUpdateDTO.memoryRemote());
        if (generalAssessmentUpdateDTO.memoryRecent() != null) entity.setMemoryRecent(generalAssessmentUpdateDTO.memoryRecent());
        if (generalAssessmentUpdateDTO.signsOfAgitation() != null) entity.setSignsOfAgitation(generalAssessmentUpdateDTO.signsOfAgitation());
        if (generalAssessmentUpdateDTO.signsOfDepression() != null) entity.setSignsOfDepression(generalAssessmentUpdateDTO.signsOfDepression());
        if (generalAssessmentUpdateDTO.signsOfSuicidalIdeation() != null) entity.setSignsOfSuicidalIdeation(generalAssessmentUpdateDTO.signsOfSuicidalIdeation());
        if (generalAssessmentUpdateDTO.signsOfSubstanceUse() != null) entity.setSignsOfSubstanceUse(generalAssessmentUpdateDTO.signsOfSubstanceUse());
        if (generalAssessmentUpdateDTO.isTriage() != null) entity.setIsTriage(generalAssessmentUpdateDTO.isTriage());
    }

    private GeneralAssessment getRequired(Long id) {
        return generalAssessmentRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundAlertException("GeneralAssessment not found: " + id, ENTITY_NAME, "notfound"));
    }

    private Patient getPatient(Long id) {
        return patientRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundAlertException("Patient not found: " + id, "Patient", "notfound"));
    }
}