package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Hospitalization;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.domain.enumeration.Unit;
import com.dazzle.asklepios.repository.HospitalizationRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.Hospitalizations.HospitalizationCancelDTO;
import com.dazzle.asklepios.service.dto.Hospitalizations.HospitalizationsCreateDTO;
import com.dazzle.asklepios.service.dto.Hospitalizations.HospitalizationsUpdateDTO;
import com.dazzle.asklepios.service.helper.DurationDateHelper;
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

import java.math.BigDecimal;
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
    private final DurationDateHelper durationDateHelper;

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

    public Hospitalization create(HospitalizationsCreateDTO dto) {
        LOG.info("[CREATE] Hospitalization payload={}", dto);

        validate(
                dto.patientIsFree(),
                dto.freeText(),
                dto.facility(),
                dto.reason(),
                dto.admissionType(),
                dto.dateOfAdmission(),
                dto.admissionDurationType(),
                dto.admissionDuration()
        );

        boolean isFree = Boolean.TRUE.equals(dto.patientIsFree());

        Hospitalization entity = Hospitalization.builder()
                .patient(refPatient(dto.patientId()))
                .facility(isFree ? null : dto.facility())
                .reason(isFree ? null : dto.reason())
                .admissionType(isFree ? null : dto.admissionType())
                .lengthOfStayDays(isFree ? null : dto.lengthOfStayDays())
                .outcomes(isFree ? null : dto.outcomes())
                .medicalInterventionsPerformed(
                        isFree ? null : dto.medicalInterventionsPerformed()
                )
                .patientIsFree(isFree)
                .freeText(isFree ? dto.freeText().trim() : null)
                .status(PatientHistoryStatus.ACTIVE)
                .build();

        applyAdmissionDate(
                entity,
                isFree,
                dto.dateOfAdmission(),
                dto.admissionDurationType(),
                dto.admissionDuration()
        );

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

    public Hospitalization update(HospitalizationsUpdateDTO dto) {
        LOG.info("[UPDATE] Hospitalization payload={}", dto);

        validate(
                dto.patientIsFree(),
                dto.freeText(),
                dto.facility(),
                dto.reason(),
                dto.admissionType(),
                dto.dateOfAdmission(),
                dto.admissionDurationType(),
                dto.admissionDuration()
        );

        Hospitalization entity = hospitalizationRepository.findById(dto.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient admission not found with id " + dto.id(),
                        "hospitalization",
                        "notfound"
                ));

        boolean isFree = Boolean.TRUE.equals(dto.patientIsFree());

        entity.setPatient(refPatient(dto.patientId()));
        entity.setFacility(isFree ? null : dto.facility());
        entity.setReason(isFree ? null : dto.reason());
        entity.setAdmissionType(isFree ? null : dto.admissionType());
        applyAdmissionDate(
                entity,
                isFree,
                dto.dateOfAdmission(),
                dto.admissionDurationType(),
                dto.admissionDuration()
        );
        entity.setLengthOfStayDays(isFree ? null : dto.lengthOfStayDays());
        entity.setOutcomes(isFree ? null : dto.outcomes());
        entity.setMedicalInterventionsPerformed(
                isFree ? null : dto.medicalInterventionsPerformed()
        );
        entity.setPatientIsFree(isFree);
        entity.setFreeText(isFree ? dto.freeText().trim() : null);

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

    public Hospitalization cancel(HospitalizationCancelDTO dto) {
        LOG.info("[CANCEL] Hospitalization payload={}", dto);

        Hospitalization entity = hospitalizationRepository.findById(dto.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient admission not found with id " + dto.id(),
                        "hospitalization",
                        "notfound"
                ));

        entity.setStatus(PatientHistoryStatus.CANCELLED);
        entity.setCancelledBy(currentUsername());
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(dto.cancellationReason());

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

    private void validate(
            Boolean patientIsFree,
            String freeText,
            String facility,
            String reason,
            String admissionType,
            Date dateOfAdmission,
            Unit admissionDurationType,
            BigDecimal admissionDuration
    ) {
        boolean isFree = Boolean.TRUE.equals(patientIsFree);

        if (isFree) {
            if (freeText == null || freeText.trim().isEmpty()) {
                throw new BadRequestAlertException(
                        "Free text is required.",
                        "hospitalization",
                        "freeText.required"
                );
            }

            return;
        }

        if (facility == null || facility.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Facility is required.",
                    "hospitalization",
                    "facility.required"
            );
        }

        if (reason == null || reason.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Reason is required.",
                    "hospitalization",
                    "reason.required"
            );
        }

        if (admissionType == null || admissionType.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Admission type is required.",
                    "hospitalization",
                    "admissionType.required"
            );
        }

        durationDateHelper.requireComplete(
                admissionDurationType,
                admissionDuration,
                "hospitalization",
                "admissionDuration"
        );

        if (dateOfAdmission == null && admissionDurationType == null) {
            throw new BadRequestAlertException(
                    "Date of admission is required.",
                    "hospitalization",
                    "dateOfAdmission.required"
            );
        }
    }

    private void applyAdmissionDate(
            Hospitalization entity,
            boolean isFree,
            Date dateOfAdmission,
            Unit admissionDurationType,
            BigDecimal admissionDuration
    ) {
        if (isFree) {
            entity.setAdmissionDurationType(null);
            entity.setAdmissionDuration(null);
            entity.setDateOfAdmission(null);
            return;
        }

        DurationDateHelper.DurationDate resolved = durationDateHelper.resolve(
                dateOfAdmission,
                admissionDurationType,
                admissionDuration,
                entity.getAdmissionDurationType(),
                entity.getAdmissionDuration(),
                entity.getDateOfAdmission(),
                "hospitalization",
                "admissionDuration"
        );

        entity.setAdmissionDurationType(resolved.durationType());
        entity.setAdmissionDuration(resolved.duration());
        entity.setDateOfAdmission(resolved.date());
    }

    private void handleConstraints(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message =
                root != null ? root.getMessage() : exception.getMessage();

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        String lower = message != null ? message.toLowerCase() : "";

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