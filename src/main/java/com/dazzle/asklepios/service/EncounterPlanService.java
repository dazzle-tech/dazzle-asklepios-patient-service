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

    public EncounterPlan create(EncounterPlanCreateDTO dto) {
        LOG.info("[CREATE] Request to create EncounterPlan payload={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "patient",
                        "notfound"
                ));

        EncounterPlan entity = EncounterPlan.builder()
                .patient(patient)
                .encounterId(dto.encounterId())
                .planInstructions(dto.planInstructions())
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

    public EncounterPlan update(Long id, EncounterPlanUpdateDTO dto) {
        LOG.info("[UPDATE] Request to update EncounterPlan id={} payload={}", id, dto);

        EncounterPlan existing = encounterPlanRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "EncounterPlan not found with id " + id,
                        "encounterPlan",
                        "notfound"
                ));

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "patient",
                        "notfound"
                ));

        existing.setPatient(patient);
        existing.setEncounterId(dto.encounterId());
        existing.setPlanInstructions(dto.planInstructions());
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
