package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.EncounterPlan;
import com.dazzle.asklepios.domain.EncounterPlanFieldAudit;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.repository.EncounterPlanFieldAuditRepository;
import com.dazzle.asklepios.repository.EncounterPlanRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.encounterPlan.EncounterPlanCreateDTO;
import com.dazzle.asklepios.service.dto.encounterPlan.EncounterPlanUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class EncounterPlanService {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterPlanService.class);

    private final EncounterPlanRepository encounterPlanRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final EncounterPlanFieldAuditRepository encounterPlanFieldAuditRepository;

    public EncounterPlanService(
            EncounterPlanRepository encounterPlanRepository,
            PatientRepository patientRepository,
            PatientEncounterRepository patientEncounterRepository,
            EncounterPlanFieldAuditRepository encounterPlanFieldAuditRepository) {
        this.encounterPlanRepository = encounterPlanRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.encounterPlanFieldAuditRepository = encounterPlanFieldAuditRepository;
    }

    public EncounterPlan create(EncounterPlanCreateDTO createRequest) {
        LOG.info("[CREATE] Request to create EncounterPlan payload={}", createRequest);

        Patient patient = patientRepository.findById(createRequest.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + createRequest.patientId(),
                        "patient",
                        "notfound"
                ));
        PatientEncounter encounter = patientEncounterRepository.findById(createRequest.encounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + createRequest.encounterId(),
                        "patientEncounter",
                        "notfound"
                ));

        EncounterPlan entity = EncounterPlan.builder()
                .patient(patient)
                .encounterId(encounter.getId())
                .goals(createRequest.goals())
                .treatmentPlan(createRequest.treatmentPlan())
                .build();

        try {
            return encounterPlanRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while saving encounter plan.",
                    "encounterPlan",
                    "db.constraint"
            );
        }
    }

    public EncounterPlan update(Long id, EncounterPlanUpdateDTO updateRequest) {
        LOG.info("[UPDATE] Request to update EncounterPlan id={} payload={}", id, updateRequest);

        EncounterPlan existing = encounterPlanRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "EncounterPlan not found with id " + id,
                        "encounterPlan",
                        "notfound"
                ));

        Patient patient = patientRepository.findById(updateRequest.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + updateRequest.patientId(),
                        "patient",
                        "notfound"
                ));
        PatientEncounter encounter = patientEncounterRepository.findById(updateRequest.encounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + updateRequest.encounterId(),
                        "patientEncounter",
                        "notfound"
                ));

        existing.setPatient(patient);
        existing.setEncounterId(encounter.getId());
        existing.setGoals(updateRequest.goals());
        existing.setTreatmentPlan(updateRequest.treatmentPlan());
        existing.setLastModifiedDate(Instant.now());

        try {
            return encounterPlanRepository.saveAndFlush(existing);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating encounter plan.",
                    "encounterPlan",
                    "db.constraint"
            );
        }
    }

    @Transactional(readOnly = true)
    public Optional<EncounterPlan> findLatestByEncounterId(Long encounterId) {
        LOG.debug("[FIND LATEST] encounterId={}", encounterId);

        return encounterPlanRepository
                .findTopByEncounterIdOrderByCreatedDateDesc(encounterId);
    }

    @Transactional(readOnly = true)
    public Page<EncounterPlan> findByPatientId(Long patientId, Pageable pageable) {
        LOG.debug("Request to get EncounterPlans by patientId={}", patientId);
        return encounterPlanRepository.findAllByPatientIdOrderByCreatedDateDesc(patientId, pageable);
    }

    private void handleConstraintsOnCreateOrUpdate(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        String lower = (message != null ? message.toLowerCase() : "");

        if (lower.contains("uk_enc_plan_enc_patient")) {
            throw new BadRequestAlertException(
                    "Plan already exists for this patient and encounter.",
                    "encounterPlan",
                    "duplicate.record"
            );
        }

        if (lower.contains("fk_enc_plan_patient")) {
            throw new BadRequestAlertException(
                    "Invalid patient_id (patient does not exist).",
                    "encounterPlan",
                    "fk.patient"
            );
        }

        if (lower.contains("not-null") || lower.contains("null value")) {
            throw new BadRequestAlertException(
                    "Required fields are missing.",
                    "encounterPlan",
                    "required.fields"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving encounter plan.",
                "encounterPlan",
                "db.constraint"
        );
    }

    @Transactional(readOnly = true)
    public List<EncounterPlanFieldAudit> getPlanHistory(Long planId) {
        return encounterPlanFieldAuditRepository
                .findByEncounterPlanIdOrderByLogDateDesc(planId);
    }
}