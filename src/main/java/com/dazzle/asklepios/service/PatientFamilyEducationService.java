package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientFamilyEducation;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientFamilyEducationRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patientFamilyEducation.PatientFamilyEducationSaveDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class PatientFamilyEducationService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientFamilyEducationService.class);
    private static final String ENTITY = "patientFamilyEducation";

    private final PatientFamilyEducationRepository patientFamilyEducationRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final ReopenSessionAuditContext reopenSessionAuditContext;

    public PatientFamilyEducationService(
            PatientFamilyEducationRepository patientFamilyEducationRepository,
            PatientRepository patientRepository,
            PatientEncounterRepository patientEncounterRepository,
            ReopenSessionAuditContext reopenSessionAuditContext
    ) {
        this.patientFamilyEducationRepository = patientFamilyEducationRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.reopenSessionAuditContext = reopenSessionAuditContext;
    }

    public PatientFamilyEducation save(PatientFamilyEducationSaveDTO dto) {
        LOG.info(
                "Save PatientFamilyEducation id={} patientId={} encounterId={}",
                dto.id(),
                dto.patientId(),
                dto.encounterId()
        );

        Patient patient = getPatient(dto.patientId());
        PatientEncounter encounter = getEncounter(dto.encounterId());
        assertEncounterBelongsToPatient(encounter, patient);

        PatientFamilyEducation entity = resolveEntity(dto, patient, encounter);
        entity.setPatient(patient);
        entity.setEncounter(encounter);
        entity.setTopic(dto.topic().trim());
        entity.setMethod(dto.method());
        entity.setEffectiveness(blankToNull(dto.effectiveness()));

        try {
            reopenSessionAuditContext.applyOpenSession(encounter.getId());
            return patientFamilyEducationRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException | JpaSystemException e) {
            throw new BadRequestAlertException(
                    "A database constraint was violated. Please check your entries.",
                    ENTITY,
                    "db.constraint"
            );
        }
    }

    @Transactional(readOnly = true)
    public Optional<PatientFamilyEducation> findLatestByEncounterId(Long encounterId) {
        getEncounter(encounterId);
        return patientFamilyEducationRepository.findFirstByEncounter_IdOrderByIdDesc(encounterId);
    }

    private PatientFamilyEducation resolveEntity(
            PatientFamilyEducationSaveDTO dto,
            Patient patient,
            PatientEncounter encounter
    ) {
        if (dto.id() != null) {
            PatientFamilyEducation existing = patientFamilyEducationRepository.findById(dto.id())
                    .orElseThrow(() -> new BadRequestAlertException(
                            "Patient and family education record not found.",
                            ENTITY,
                            "idNotFound"
                    ));

            if (!Objects.equals(existing.getEncounterId(), encounter.getId())) {
                throw new BadRequestAlertException(
                        "This education record belongs to another encounter.",
                        ENTITY,
                        "encounterMismatch"
                );
            }

            return existing;
        }

        return patientFamilyEducationRepository.findFirstByEncounter_IdOrderByIdDesc(encounter.getId())
                .orElseGet(() -> PatientFamilyEducation.builder()
                        .patient(patient)
                        .encounter(encounter)
                        .topic(dto.topic().trim())
                        .build());
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

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
