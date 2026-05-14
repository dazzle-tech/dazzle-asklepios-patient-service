package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.Hospitalization;
import com.dazzle.asklepios.repository.HospitalizationRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.Hospitalizations.HospitalizationsCreateDTO;
import com.dazzle.asklepios.service.dto.Hospitalizations.HospitalizationsUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.dazzle.asklepios.service.dto.Hospitalizations.HospitalizationCancelDTO;
import java.util.Date;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class HospitalizationsService {

    private static final Logger LOG =
            LoggerFactory.getLogger(HospitalizationsService.class);

    private final HospitalizationRepository hospitalizationRepository;
    private final PatientRepository patientRepository;

    private Patient refPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        "patient",
                        "notfound"
                ));
    }

    public Hospitalization create(HospitalizationsCreateDTO hospitalizationsCreateDTO) {
        LOG.info("[CREATE] Hospitalization payload={}", hospitalizationsCreateDTO);

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
            return hospitalizationRepository.saveAndFlush(entity);

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

        Hospitalization entity = hospitalizationRepository.findById(hospitalizationsUpdateDTO.id())
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
            return hospitalizationRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating patient admission.",
                    "hospitalization",
                    "db.constraint"
            );
        }
    }

    public Hospitalization cancel(HospitalizationCancelDTO hospitalizationCancelDTO) {
        LOG.info("[CANCEL] Hospitalization payload={}", hospitalizationCancelDTO);

        Hospitalization entity = hospitalizationRepository.findById(hospitalizationCancelDTO.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient admission not found with id " + hospitalizationCancelDTO.id(),
                        "hospitalization",
                        "notfound"
                ));

        entity.setStatus("CANCELLED");
        entity.setCancelledBy(
                entity.getLastModifiedBy() != null
                        ? entity.getLastModifiedBy()
                        : entity.getCreatedBy()
        );
        entity.setCancelledDate(new Date());
        entity.setCancellationReason(hospitalizationCancelDTO.cancellationReason());

        try {
            return hospitalizationRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while cancelling patient admission.",
                    "hospitalization",
                    "db.constraint"
            );
        }
    }

    public void delete(Long id) {
        LOG.info("[DELETE] Hospitalization id={}", id);

        Hospitalization entity = hospitalizationRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient admission not found with id " + id,
                        "hospitalization",
                        "notfound"
                ));

        hospitalizationRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public Page<Hospitalization> findByPatientId(Long patientId, Pageable pageable) {
        LOG.debug("[LIST] Hospitalization patientId={} pageable={}", patientId, pageable);
        return hospitalizationRepository.findAllByPatientId(patientId, pageable);
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