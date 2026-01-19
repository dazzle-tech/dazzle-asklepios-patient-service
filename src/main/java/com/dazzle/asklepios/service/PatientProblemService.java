package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientProblem;
import com.dazzle.asklepios.repository.PatientProblemRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.PatientProblems.PatientProblemCreateDTO;
import com.dazzle.asklepios.service.dto.PatientProblems.PatientProblemUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientProblemService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PatientProblemService.class);

    private final PatientProblemRepository repository;
    private final PatientRepository patientRepository;

    private Patient refPatient(Long patientId) {
        return entityManager.getReference(Patient.class, patientId);
    }

    @PersistenceContext
    private EntityManager entityManager;


    public PatientProblem create(PatientProblemCreateDTO patientProblemCreateDTO) {
        LOG.info("[CREATE] PatientProblem payload={}", patientProblemCreateDTO);

        if (patientProblemCreateDTO == null) {
            throw new BadRequestAlertException(
                    "Patient problem payload is required",
                    "patientProblem",
                    "payload.required"
            );
        }


        PatientProblem entity = PatientProblem.builder()
                .patient(refPatient(patientProblemCreateDTO.patientId()))
                .condition(patientProblemCreateDTO.condition())
                .dateOfDiagnosis(patientProblemCreateDTO.dateOfDiagnosis())
                .status(patientProblemCreateDTO.status())
                .type(patientProblemCreateDTO.type())
                .dateOfResolution(patientProblemCreateDTO.dateOfResolution())
                .byPatient(patientProblemCreateDTO.byPatient())
                .sourceOfInformation(patientProblemCreateDTO.sourceOfInformation())
                .build();

        try {
            PatientProblem saved = repository.saveAndFlush(entity);
            entityManager.refresh(saved);
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while creating patient problem.",
                    "patientProblem",
                    "db.constraint"
            );
        }
    }


    public PatientProblem update(PatientProblemUpdateDTO patientProblemUpdateDTO) {
        LOG.info("[UPDATE] PatientProblem payload={}", patientProblemUpdateDTO);

        PatientProblem entity = repository.findById(patientProblemUpdateDTO.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient problem not found with id " + patientProblemUpdateDTO.id(),
                        "patientProblem",
                        "notfound"
                ));


        entity.setPatient(refPatient((patientProblemUpdateDTO.patientId())));
        entity.setCondition(patientProblemUpdateDTO.condition());
        entity.setDateOfDiagnosis(patientProblemUpdateDTO.dateOfDiagnosis());
        entity.setStatus(patientProblemUpdateDTO.status());
        entity.setType(patientProblemUpdateDTO.type());
        entity.setDateOfResolution(patientProblemUpdateDTO.dateOfResolution());
        entity.setByPatient(patientProblemUpdateDTO.byPatient());
        entity.setSourceOfInformation(patientProblemUpdateDTO.sourceOfInformation());

        try {
            PatientProblem updated = repository.saveAndFlush(entity);
            entityManager.refresh(updated);
            return updated;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating patient problem.",
                    "patientProblem",
                    "db.constraint"
            );
        }
    }


    public void delete(Long id) {
        LOG.info("[DELETE] PatientProblem id={}", id);

        PatientProblem entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient problem not found with id " + id,
                        "patientProblem",
                        "notfound"
                ));

        repository.delete(entity);
    }


    @Transactional(readOnly = true)
    public Page<PatientProblem> findByPatientId(Long patientId, Pageable pageable) {
        LOG.debug("[LIST] PatientProblems patientId={} pageable={}", patientId, pageable);
        return repository.findAllByPatientId(patientId, pageable);
    }


    private void handleConstraints(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        String lower = (message != null ? message.toLowerCase() : "");

        if (lower.contains("ck_patient_problems_source_required")
                || (lower.contains("check constraint") && lower.contains("source"))) {
            throw new BadRequestAlertException(
                    "Source of information is required when problem is not reported by patient.",
                    "patientProblem",
                    "source.required"
            );
        }

        if (lower.contains("fk_patient_problems_patient")
                || (lower.contains("foreign key") && lower.contains("patient"))) {
            throw new BadRequestAlertException(
                    "Invalid patient reference.",
                    "patientProblem",
                    "patient.invalid"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving patient problem.",
                "patientProblem",
                "db.constraint"
        );
    }
}
