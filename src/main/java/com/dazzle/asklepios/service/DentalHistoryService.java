package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DentalHistory;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.repository.DentalHistoryRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.dentalHistory.DentalHistoryCreateDTO;
import com.dazzle.asklepios.service.dto.dentalHistory.DentalHistoryUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional
public class DentalHistoryService {

    private static final Logger LOG = LoggerFactory.getLogger(DentalHistoryService.class);
    private static final String ENTITY = "dentalHistory";

    private final DentalHistoryRepository dentalHistoryRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final ReopenSessionAuditContext reopenSessionAuditContext;

    public DentalHistoryService(
            DentalHistoryRepository dentalHistoryRepository,
            PatientRepository patientRepository,
            PatientEncounterRepository patientEncounterRepository,
            ReopenSessionAuditContext reopenSessionAuditContext
    ) {
        this.dentalHistoryRepository = dentalHistoryRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.reopenSessionAuditContext = reopenSessionAuditContext;
    }

    public DentalHistory create(DentalHistoryCreateDTO dto) {
        LOG.info("Create DentalHistory patientId={} encounterId={}", dto.patientId(), dto.encounterId());

        Patient patient = getPatient(dto.patientId());
        PatientEncounter encounter = getEncounter(dto.encounterId());
        assertEncounterBelongsToPatient(encounter, patient);

        DentalHistory entity = DentalHistory.builder()
                .patient(patient)
                .encounter(encounter)
                .historyText(dto.historyText().trim())
                .build();

        try {
            reopenSessionAuditContext.applyOpenSession(encounter.getId());
            return dentalHistoryRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException | JpaSystemException e) {
            throw constraintViolation();
        }
    }

    public DentalHistory update(DentalHistoryUpdateDTO dto) {
        DentalHistory entity = dentalHistoryRepository.findById(dto.id())
                .orElseThrow(() -> new BadRequestAlertException(
                        "Dental history not found.",
                        ENTITY,
                        "idNotFound"
                ));

        Long encounterId = entity.getEncounterId();
        entity.setHistoryText(dto.historyText().trim());

        try {
            reopenSessionAuditContext.applyOpenSession(encounterId);
            return dentalHistoryRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException | JpaSystemException e) {
            throw constraintViolation();
        }
    }

    @Transactional(readOnly = true)
    public Page<DentalHistory> findAllByPatientId(Long patientId, Pageable pageable) {
        getPatient(patientId);
        return dentalHistoryRepository.findByPatient_Id(patientId, pageable);
    }

    private Patient getPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Patient not found.",
                        ENTITY,
                        "patientNotFound"
                ));
    }

    private PatientEncounter getEncounter(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found.",
                        ENTITY,
                        "encounterNotFound"
                ));
    }

    private void assertEncounterBelongsToPatient(PatientEncounter encounter, Patient patient) {
        Long encounterPatientId = encounter.getPatient() == null ? null : encounter.getPatient().getId();
        if (!Objects.equals(encounterPatientId, patient.getId())) {
            throw new BadRequestAlertException(
                    "Encounter does not belong to this patient.",
                    ENTITY,
                    "encounterPatientMismatch"
            );
        }
    }

    private BadRequestAlertException constraintViolation() {
        return new BadRequestAlertException(
                "A database constraint was violated. Please check your entries.",
                ENTITY,
                "db.constraint"
        );
    }
}
