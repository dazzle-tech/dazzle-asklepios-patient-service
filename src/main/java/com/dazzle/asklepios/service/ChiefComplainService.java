package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ChiefComplain;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.repository.ChiefComplainRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.chiefComplain.ChiefComplainCreateDTO;
import com.dazzle.asklepios.service.dto.chiefComplain.ChiefComplainUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChiefComplainService {

    private static final Logger LOG = LoggerFactory.getLogger(ChiefComplainService.class);
    private static final String ENTITY_NAME = "ChiefComplain";

    private final ChiefComplainRepository chiefComplainRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;

    public ChiefComplainService(ChiefComplainRepository chiefComplainRepository, PatientRepository patientRepository, PatientEncounterRepository patientEncounterRepository) {
        this.chiefComplainRepository = chiefComplainRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
    }

    public ChiefComplain create(ChiefComplainCreateDTO dto) {
        LOG.debug("create chief complain {}", dto);
        ChiefComplain entity = toEntityForCreate(dto);
        return chiefComplainRepository.save(entity);
    }

    public ChiefComplain update(ChiefComplainUpdateDTO dto) {
        LOG.debug("update chief complain {}", dto);
        ChiefComplain entity = getRequired(dto.id());
        applyUpdate(entity, dto);
        return chiefComplainRepository.save(entity);

    }


    @Transactional
    public void hardDelete(Long id) {
        LOG.debug("delete chief complain by id : '{}'", id);
        chiefComplainRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public ChiefComplain getOneByEncounterId(Long encounterId) {
        LOG.debug("get latest chief complain by encounterId={}", encounterId);

        return chiefComplainRepository.findTopByEncounter_IdOrderByCreatedDateDesc(encounterId).orElseThrow(() -> new NotFoundAlertException("ChiefComplain not found for encounter: " + encounterId, ENTITY_NAME, "notfound"));
    }

    @Transactional(readOnly = true)
    public ChiefComplain getLatestTriageByEncounterId(Long encounterId) {
        LOG.debug("get latest triage ChiefComplain by encounterId={}", encounterId);
        return chiefComplainRepository.findTopByEncounter_IdAndIsTriageTrueOrderByCreatedDateDesc(encounterId).orElseThrow(() -> new NotFoundAlertException("Triage ChiefComplain not found for encounter: " + encounterId, ENTITY_NAME, "notfound"));
    }

    private ChiefComplain getRequired(Long id) {
        return chiefComplainRepository.findById(id).orElseThrow(() -> new NotFoundAlertException("ChiefComplain not found: " + id, ENTITY_NAME, "notfound"));
    }

    private ChiefComplain toEntityForCreate(ChiefComplainCreateDTO dto) {
        Patient patient = getPatient(dto.patientId());
        PatientEncounter encounter=getEncounter(dto.encounterId());

        return ChiefComplain.builder().patient(patient).encounter(encounter).chiefComplaint(dto.chiefComplaint()).provocation(dto.provocation()).palliation(dto.palliation()).quality(dto.quality()).region(dto.region()).severity(dto.severity()).onsetDateTime(dto.onsetDateTime()).caseUnderstanding(dto.caseUnderstanding()).patientCondition(dto.patientCondition()).isTriage(dto.isTriage()).build();
    }

    private void applyUpdate(ChiefComplain entity, ChiefComplainUpdateDTO dto) {
        if (dto.chiefComplaint() != null) entity.setChiefComplaint(dto.chiefComplaint());
        if (dto.provocation() != null) entity.setProvocation(dto.provocation());
        if (dto.palliation() != null) entity.setPalliation(dto.palliation());
        if (dto.quality() != null) entity.setQuality(dto.quality());
        if (dto.region() != null) entity.setRegion(dto.region());
        if (dto.severity() != null) entity.setSeverity(dto.severity());
        if (dto.onsetDateTime() != null) entity.setOnsetDateTime(dto.onsetDateTime());
        if (dto.caseUnderstanding() != null) entity.setCaseUnderstanding(dto.caseUnderstanding());
        if (dto.patientCondition() != null) entity.setPatientCondition(dto.patientCondition());
        if (dto.isTriage() != null) entity.setIsTriage(dto.isTriage());
    }

    private Patient getPatient(Long id) {
        return patientRepository.findById(id).orElseThrow(() -> new NotFoundAlertException("Patient not found: " + id, "Patient", "notfound"));
    }
    private PatientEncounter getEncounter(Long id) {
        return patientEncounterRepository.findById(id).orElseThrow(() -> new NotFoundAlertException("Patient Encounter not found: " + id, "PatientEncounter", "notfound"));
    }
}