package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientSickLeave;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientSickLeaveRepository;
import com.dazzle.asklepios.service.dto.patientSickLeave.PatientSickLeaveCreateDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class PatientSickLeaveService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientSickLeaveService.class);

    private final PatientSickLeaveRepository patientSickLeaveRepository;
    private final PatientEncounterRepository patientEncounterRepository;

    public PatientSickLeaveService(PatientSickLeaveRepository patientSickLeaveRepository, PatientEncounterRepository patientEncounterRepository) {
        this.patientSickLeaveRepository = patientSickLeaveRepository;
        this.patientEncounterRepository = patientEncounterRepository;
    }

    public PatientSickLeave create(PatientSickLeaveCreateDTO dto) {
        LOG.debug("create PatientSickLeave for encounterId={}", dto.encounterId());

        PatientEncounter encounter = patientEncounterRepository.findById(dto.encounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient Encounter not found: " + dto.encounterId(), "PatientEncounter", "notfound"));

        PatientSickLeave sickLeave = new PatientSickLeave();
        sickLeave.setPatientId(encounter.getPatient().getId());
        sickLeave.setEncounterId(dto.encounterId());
        sickLeave.setStartDate(dto.startDate());
        sickLeave.setEndDate(dto.endDate());
        sickLeave.setNotes(dto.notes());
        sickLeave.setLanguage(dto.language() != null ? dto.language() : "en");

        return patientSickLeaveRepository.save(sickLeave);
    }

    @Transactional(readOnly = true)
    public List<PatientSickLeave> getByPatientId(Long patientId, LocalDate startDate, LocalDate endDate) {
        LOG.debug("get PatientSickLeave for patientId={}", patientId);
        if (startDate != null && endDate != null) {
            return patientSickLeaveRepository
                    .findByPatientIdAndStartDateGreaterThanEqualAndEndDateLessThanEqualOrderByStartDateDesc(patientId, startDate, endDate);
        }
        if (startDate != null) {
            return patientSickLeaveRepository.findByPatientIdAndStartDateGreaterThanEqualOrderByStartDateDesc(patientId, startDate);
        }
        if (endDate != null) {
            return patientSickLeaveRepository.findByPatientIdAndEndDateLessThanEqualOrderByStartDateDesc(patientId, endDate);
        }
        return patientSickLeaveRepository.findByPatientIdOrderByStartDateDesc(patientId);
    }

    @Transactional(readOnly = true)
    public List<PatientSickLeave> getByEncounterId(Long encounterId) {
        LOG.debug("get PatientSickLeave for encounterId={}", encounterId);
        return patientSickLeaveRepository.findByEncounterIdOrderByStartDateDesc(encounterId);
    }
}
