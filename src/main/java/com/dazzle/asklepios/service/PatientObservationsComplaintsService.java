package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientObservationsComplaints;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientObservationsComplaintsRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patientObservationsComplaints.PatientObservationsComplaintsCreateDTO;
import com.dazzle.asklepios.service.dto.patientObservationsComplaints.PatientObservationsComplaintsUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientObservationsComplaintsService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientObservationsComplaintsService.class);

    private final PatientObservationsComplaintsRepository patientObservationsComplaintsRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;

    public PatientObservationsComplaints create(PatientObservationsComplaintsCreateDTO dto) {
        LOG.info("[CREATE] PatientObservationsComplaints payload={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "patientObservationsComplaints",
                        "patient.notfound"
                ));
        PatientEncounter encounter = patientEncounterRepository.findById(dto.encounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + dto.encounterId(),
                        "painAssessment",
                        "encounter.notfound"
                ));

        PatientObservationsComplaints entity = PatientObservationsComplaints.builder()
                .patient(patient)
                .encounterId(encounter.getId())
                .functionalStatus(dto.functionalStatus())
                .reasonOfVisit(dto.reasonOfVisit())
                .cognitiveCheck(dto.cognitiveCheck())
                .patientConditions(dto.patientConditions())
                .bloodGroup(dto.bloodGroup())
                .isActive(dto.isActive())
                .build();

        try {
            return patientObservationsComplaintsRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    public Optional<PatientObservationsComplaints> update(Long id, PatientObservationsComplaintsUpdateDTO dto) {
        Long targetId = id != null ? id : dto.id();
        LOG.info("[UPDATE] PatientObservationsComplaints id={} payload={}", targetId, dto);

        return patientObservationsComplaintsRepository.findById(targetId).map(entity -> {

            Patient patient = patientRepository.findById(dto.patientId())
                    .orElseThrow(() -> new NotFoundAlertException(
                            "Patient not found with id " + dto.patientId(),
                            "patientObservationsComplaints",
                            "patient.notfound"
                    ));

            entity.setPatient(patient);
            entity.setEncounterId(dto.encounterId());
            entity.setFunctionalStatus(dto.functionalStatus());
            entity.setReasonOfVisit(dto.reasonOfVisit());
            entity.setCognitiveCheck(dto.cognitiveCheck());
            entity.setPatientConditions(dto.patientConditions());
            entity.setBloodGroup(dto.bloodGroup());
            entity.setIsActive(dto.isActive());

            try {
                return patientObservationsComplaintsRepository.saveAndFlush(entity);
            } catch (DataIntegrityViolationException | JpaSystemException ex) {
                throw handleConstraintViolation(ex);
            }
        });
    }


    @Transactional(readOnly = true)
    public Optional<PatientObservationsComplaints> findLatestByEncounterId(Long encounterId) {
        LOG.debug("[FIND_LATEST_BY_ENCOUNTER] encounterId={}", encounterId);
        return patientObservationsComplaintsRepository
                .findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounterId);
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] PatientObservationsComplaints constraint violated rootMessage={}", message, exception);

        if (messageLower.contains("fk_patient_obs_comp_patient")) {
            return new BadRequestAlertException(
                    "Invalid patient id.",
                    "patientObservationsComplaints",
                    "patient.invalid"
            );
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving patient observations & complaints.",
                "patientObservationsComplaints",
                "db.constraint"
        );
    }
}
