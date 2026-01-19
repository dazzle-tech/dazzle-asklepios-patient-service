package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.Hospitalization;
import com.dazzle.asklepios.repository.HospitalizationRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.Hospitalizations.HospitalizationsCreateDTO;
import com.dazzle.asklepios.service.dto.Hospitalizations.HospitalizationsUpdateDTO;
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
public class HospitalizationsService {

    private static final Logger LOG =
            LoggerFactory.getLogger(HospitalizationsService.class);

    private final HospitalizationRepository repository;
    private final PatientRepository patientRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Patient refPatient(Long patientId) {
        return entityManager.getReference(Patient.class, patientId);
    }


    public Hospitalization create(HospitalizationsCreateDTO hospitalizationsCreateDTO) {
        LOG.info("[CREATE] Hospitalization payload={}", hospitalizationsCreateDTO);

        if (hospitalizationsCreateDTO == null) {
            throw new BadRequestAlertException(
                    "Patient admission payload is required",
                    "hospitalization",
                    "payload.required"
            );
        }

        Hospitalization entity = Hospitalization.builder()
                .patient(refPatient(hospitalizationsCreateDTO.patientId()))
                .facility(hospitalizationsCreateDTO.facility())
                .reason(hospitalizationsCreateDTO.reason())
                .admissionType(hospitalizationsCreateDTO.admissionType())
                .dateOfAdmission(hospitalizationsCreateDTO.dateOfAdmission())
                .lengthOfStayDays(hospitalizationsCreateDTO.lengthOfStayDays())
                .outcomes(hospitalizationsCreateDTO.outcomes())
                .medicalInterventionsPerformed(hospitalizationsCreateDTO.medicalInterventionsPerformed())
                .build();

        try {
            Hospitalization saved = repository.saveAndFlush(entity);
            entityManager.refresh(saved);
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while creating patient admission.",
                    "hospitalization",
                    "db.constraint"
            );
        }
    }


    public Hospitalization update(HospitalizationsUpdateDTO hospitalizationsUpdateDTO) {
        LOG.info("[UPDATE] Hospitalization payload={}", hospitalizationsUpdateDTO);

        Hospitalization entity = repository.findById(hospitalizationsUpdateDTO.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient admission not found with id " + hospitalizationsUpdateDTO.id(),
                        "hospitalization",
                        "notfound"
                ));

        entity.setPatient(refPatient(hospitalizationsUpdateDTO.patientId()));
        entity.setFacility(hospitalizationsUpdateDTO.facility());
        entity.setReason(hospitalizationsUpdateDTO.reason());
        entity.setAdmissionType(hospitalizationsUpdateDTO.admissionType());
        entity.setDateOfAdmission(hospitalizationsUpdateDTO.dateOfAdmission());
        entity.setLengthOfStayDays(hospitalizationsUpdateDTO.lengthOfStayDays());
        entity.setOutcomes(hospitalizationsUpdateDTO.outcomes());
        entity.setMedicalInterventionsPerformed(hospitalizationsUpdateDTO.medicalInterventionsPerformed());

        try {
            Hospitalization updated = repository.saveAndFlush(entity);
            entityManager.refresh(updated);
            return updated;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating patient admission.",
                    "hospitalization",
                    "db.constraint"
            );
        }
    }


    public void delete(Long id) {
        LOG.info("[DELETE] Hospitalization id={}", id);

        Hospitalization entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient admission not found with id " + id,
                        "hospitalization",
                        "notfound"
                ));

        repository.delete(entity);
    }


    @Transactional(readOnly = true)
    public Page<Hospitalization> findByPatientId(Long patientId, Pageable pageable) {
        LOG.debug("[LIST] Hospitalization patientId={} pageable={}", patientId, pageable);
        return repository.findAllByPatientId(patientId, pageable);
    }


    private void handleConstraints(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        String lower = (message != null ? message.toLowerCase() : "");

        if (lower.contains("ux_patient_admissions_patient_facility_date")
                || (lower.contains("unique") && lower.contains("facility"))) {
            throw new BadRequestAlertException(
                    "Patient admission already exists for this patient, facility and admission date.",
                    "hospitalization",
                    "duplicate"
            );
        }


        if (lower.contains("fk_patient_admissions_patient")
                || (lower.contains("foreign key") && lower.contains("patient"))) {
            throw new BadRequestAlertException(
                    "Invalid patient reference.",
                    "hospitalization",
                    "patient.invalid"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving patient admission.",
                "hospitalization",
                "db.constraint"
        );
    }
}
