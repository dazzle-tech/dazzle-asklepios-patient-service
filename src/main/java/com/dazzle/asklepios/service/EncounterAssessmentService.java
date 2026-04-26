package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.EncounterAssessment;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.EncounterAssessmentRepository;
import com.dazzle.asklepios.repository.PatientRepository;

import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.encounterAssessment.EncounterAssessmentCreateDTO;
import com.dazzle.asklepios.service.dto.encounterAssessment.EncounterAssessmentUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class EncounterAssessmentService {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterAssessmentService.class);

    private final EncounterAssessmentRepository encounterAssessmentRepository;
    private final PatientRepository patientRepository;

    public EncounterAssessmentService(
            EncounterAssessmentRepository encounterAssessmentRepository,
            PatientRepository patientRepository
    ) {
        this.encounterAssessmentRepository = encounterAssessmentRepository;
        this.patientRepository = patientRepository;
    }

    public EncounterAssessment create(EncounterAssessmentCreateDTO createRequest) {
        LOG.info("[CREATE] Request to create EncounterAssessment payload={}", createRequest);

        Patient patient = patientRepository.findById(createRequest.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + createRequest.patientId(),
                        "patient",
                        "notfound"
                ));

        EncounterAssessment entity = EncounterAssessment.builder()
                .patient(patient)
                .encounterId(createRequest.encounterId())
                .assessment(createRequest.assessment())
                .build();

        try {
            return encounterAssessmentRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while saving encounter assessment.",
                    "encounterAssessment",
                    "db.constraint"
            );
        }
    }

    public EncounterAssessment update(Long id, EncounterAssessmentUpdateDTO updateRequest) {
        LOG.info("[UPDATE] Request to update EncounterAssessment id={} payload={}", id, updateRequest);

        EncounterAssessment existing = encounterAssessmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "EncounterAssessment not found with id " + id,
                        "encounterAssessment",
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
        existing.setAssessment(updateRequest.assessment());
        existing.setLastModifiedDate(Instant.now());

        try {
            return encounterAssessmentRepository.saveAndFlush(existing);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating encounter assessment.",
                    "encounterAssessment",
                    "db.constraint"
            );
        }
    }

    @Transactional(readOnly = true)
    public Optional<EncounterAssessment> findLatestByEncounterId(Long encounterId) {

        String currentUser = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "Current user not found",
                        "encounterAssessment",
                        "user.notfound"
                ));

        LOG.debug("[FIND LATEST] encounterId={} createdBy={}", encounterId, currentUser);

        return encounterAssessmentRepository
                .findTopByEncounterIdAndCreatedByOrderByCreatedDateDesc(encounterId, currentUser);
    }

    private void handleConstraintsOnCreateOrUpdate(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        String lower = (message != null ? message.toLowerCase() : "");

        if (lower.contains("uk_enc_assessment_enc_patient")) {
            throw new BadRequestAlertException(
                    "Assessment already exists for this patient, encounter, and user.",
                    "encounterAssessment",
                    "duplicate.record"
            );
        }

        if (lower.contains("fk_enc_assessment_patient")) {
            throw new BadRequestAlertException(
                    "Invalid patient_id (patient does not exist).",
                    "encounterAssessment",
                    "fk.patient"
            );
        }

        if (lower.contains("fk_enc_assessment_user")) {
            throw new BadRequestAlertException(
                    "Invalid user_id (user does not exist).",
                    "encounterAssessment",
                    "fk.user"
            );
        }

        if (lower.contains("not-null") || lower.contains("null value")) {
            throw new BadRequestAlertException(
                    "Required fields are missing.",
                    "encounterAssessment",
                    "required.fields"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving encounter assessment.",
                "encounterAssessment",
                "db.constraint"
        );
    }
}
