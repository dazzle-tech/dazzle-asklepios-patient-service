package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Hospitalization;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.repository.HospitalizationRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.Hospitalizations.HospitalizationCancelDTO;
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

import java.time.Instant;
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

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "hospitalization",
                        "No authenticated user"
                ));
    }


    public Hospitalization create(HospitalizationsCreateDTO hospitalizationCreateDTO) {
        LOG.info("[CREATE] Hospitalization payload={}", hospitalizationCreateDTO);

        Hospitalization entity = Hospitalization.builder()
                .patient(refPatient(hospitalizationCreateDTO.patientId()))
                .facility(hospitalizationCreateDTO.facility())
                .reason(hospitalizationCreateDTO.reason())
                .admissionType(hospitalizationCreateDTO.admissionType())
                .dateOfAdmission(hospitalizationCreateDTO.dateOfAdmission())
                .lengthOfStayDays(hospitalizationCreateDTO.lengthOfStayDays())
                .outcomes(hospitalizationCreateDTO.outcomes())
                .medicalInterventionsPerformed(
                        hospitalizationCreateDTO.medicalInterventionsPerformed()
                )

                .status(PatientHistoryStatus.ACTIVE)

                .build();

        try {
            return hospitalizationRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraints(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while creating hospitalization.",
                    "hospitalization",
                    "db.constraint"
            );
        }
    }

    public Hospitalization update(HospitalizationsUpdateDTO hospitalizationsUpdateDTO) {
        LOG.info("[UPDATE] Hospitalization payload={}", hospitalizationsUpdateDTO);

        Hospitalization entity = hospitalizationRepository.findById(
                        hospitalizationsUpdateDTO.id()
                )
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient admission not found with id "
                                + hospitalizationsUpdateDTO.id(),
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
        entity.setMedicalInterventionsPerformed(
                hospitalizationsUpdateDTO.medicalInterventionsPerformed()
        );

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

        Hospitalization entity = hospitalizationRepository.findById(
                        hospitalizationCancelDTO.id()
                )
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient admission not found with id "
                                + hospitalizationCancelDTO.id(),
                        "hospitalization",
                        "notfound"
                ));

        entity.setStatus(PatientHistoryStatus.CANCELLED);
        entity.setCancelledBy(currentUsername());
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(
                hospitalizationCancelDTO.cancellationReason()
        );

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
    public Page<Hospitalization> findByPatientId(
            Long patientId,
            boolean showCancelled,
            Pageable pageable
    ) {
        LOG.debug(
                "[LIST] Hospitalization patientId={} showCancelled={} pageable={}",
                patientId,
                showCancelled,
                pageable
        );

        if (showCancelled) {
            return hospitalizationRepository.findAllByPatientId(
                    patientId,
                    pageable
            );
        }

        return hospitalizationRepository.findAllByPatientIdAndStatusNot(
                patientId,
                PatientHistoryStatus.CANCELLED,
                pageable
        );
    }

    private void handleConstraints(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message =
                (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        String lower = (message != null ? message.toLowerCase() : "");

        if (lower.contains("ux_patient_admissions_patient_facility_date")
                || (lower.contains("unique")
                && lower.contains("facility"))) {
            throw new BadRequestAlertException(
                    "Patient admission already exists for this patient, facility and admission date.",
                    "hospitalization",
                    "duplicate"
            );
        }

        if (lower.contains("fk_patient_admissions_patient")
                || (lower.contains("foreign key")
                && lower.contains("patient"))) {
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