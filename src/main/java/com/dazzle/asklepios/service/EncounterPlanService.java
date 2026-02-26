package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.EncounterPlan;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.EncounterPlanRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.encounterPlan.EncounterPlanCreateDTO;
import com.dazzle.asklepios.service.dto.encounterPlan.EncounterPlanUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class EncounterPlanService {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterPlanService.class);

    private final EncounterPlanRepository encounterPlanRepository;
    private final PatientRepository patientRepository;

    public EncounterPlanService(
            EncounterPlanRepository encounterPlanRepository,
            PatientRepository patientRepository
    ) {
        this.encounterPlanRepository = encounterPlanRepository;
        this.patientRepository = patientRepository;
    }

    public EncounterPlan create(EncounterPlanCreateDTO createRequest) {
        LOG.info("[CREATE] Request to create EncounterPlan payload={}", createRequest);

        Patient patient = patientRepository.findById(createRequest.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + createRequest.patientId(),
                        "patient",
                        "notfound"
                ));

        EncounterPlan entity = EncounterPlan.builder()
                .patient(patient)
                .encounterId(createRequest.encounterId())
                .planInstructions(createRequest.planInstructions())
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

        existing.setPatient(patient);
        existing.setEncounterId(updateRequest.encounterId());
        existing.setPlanInstructions(updateRequest.planInstructions());
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
    public EncounterPlan findLatestByEncounterId(Long encounterId) {
        LOG.debug("[FIND LATEST] encounterId={}", encounterId);

        return encounterPlanRepository
                .findTopByEncounterIdOrderByCreatedDateDesc(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "No encounter plan found for encounterId=" + encounterId,
                        "encounterPlan",
                        "notfound"
                ));
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
}
