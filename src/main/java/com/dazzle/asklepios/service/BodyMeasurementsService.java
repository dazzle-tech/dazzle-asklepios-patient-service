package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BodyMeasurements;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.bodyMeasurements.BodyMeasurementsCreateDTO;
import com.dazzle.asklepios.service.dto.bodyMeasurements.BodyMeasurementsUpdateDTO;
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
public class BodyMeasurementsService {

    private static final Logger LOG = LoggerFactory.getLogger(BodyMeasurementsService.class);

    private final BodyMeasurementsRepository bodyMeasurementsRepository;
    private final PatientRepository patientRepository;

    public BodyMeasurements create(BodyMeasurementsCreateDTO dto) {
        LOG.info("[CREATE] BodyMeasurements payload={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "bodyMeasurements",
                        "patient.notfound"
                ));

        BodyMeasurements entity = BodyMeasurements.builder()
                .patient(patient)
                .encounterId(dto.encounterId())
                .weight(dto.weight())
                .height(dto.height())
                .headCircumference(dto.headCircumference())
                .isActive(dto.isActive())
                .build();

        try {
            return bodyMeasurementsRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    public Optional<BodyMeasurements> update(Long id, BodyMeasurementsUpdateDTO dto) {
        Long targetId = id != null ? id : dto.id();
        LOG.info("[UPDATE] BodyMeasurements id={} payload={}", targetId, dto);

        return bodyMeasurementsRepository.findById(targetId).map(entity -> {

            Patient patient = patientRepository.findById(dto.patientId())
                    .orElseThrow(() -> new NotFoundAlertException(
                            "Patient not found with id " + dto.patientId(),
                            "bodyMeasurements",
                            "patient.notfound"
                    ));

            entity.setPatient(patient);
            entity.setEncounterId(dto.encounterId());
            entity.setWeight(dto.weight());
            entity.setHeight(dto.height());
            entity.setHeadCircumference(dto.headCircumference());
            entity.setIsActive(dto.isActive());

            try {
                return bodyMeasurementsRepository.saveAndFlush(entity);
            } catch (DataIntegrityViolationException | JpaSystemException ex) {
                throw handleConstraintViolation(ex);
            }
        });
    }

    @Transactional(readOnly = true)
    public Optional<BodyMeasurements> findLatestByPatientId(Long patientId) {
        LOG.debug("[FIND_LATEST_BY_PATIENT] patientId={}", patientId);
        return bodyMeasurementsRepository.findFirstByPatient_IdAndIsActiveTrueOrderByCreatedDateDesc(patientId);
    }

    @Transactional(readOnly = true)
    public Optional<BodyMeasurements> findLatestByEncounterId(Long encounterId) {
        LOG.debug("[FIND_LATEST_BY_ENCOUNTER] encounterId={}", encounterId);
        return bodyMeasurementsRepository.findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounterId);
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] BodyMeasurements constraint violated rootMessage={}", message, exception);

        if (messageLower.contains("fk_body_measurements_patient")) {
            return new BadRequestAlertException("Invalid patient id.", "bodyMeasurements", "patient.invalid");
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving body measurements.",
                "bodyMeasurements",
                "db.constraint"
        );
    }
}
