package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.VitalSigns;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import com.dazzle.asklepios.service.dto.vitalSigns.VitalSignsCreateDTO;
import com.dazzle.asklepios.service.dto.vitalSigns.VitalSignsUpdateDTO;
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
public class VitalSignsService {

    private static final Logger LOG = LoggerFactory.getLogger(VitalSignsService.class);

    private final VitalSignsRepository vitalSignsRepository;
    private final PatientRepository patientRepository;


    public VitalSigns create(VitalSignsCreateDTO dto) {
        LOG.info("[CREATE] VitalSigns payload={}", dto);

        if (dto == null) {
            throw new BadRequestAlertException("VitalSigns payload is required", "vitalSigns", "payload.required");
        }

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "vitalSigns",
                        "patient.notfound"
                ));

        VitalSigns entity = VitalSigns.builder()
                .patient(patient)
                .encounterId(dto.encounterId())
                .bloodPressureSystolic(dto.bloodPressureSystolic())
                .bloodPressureDiastolic(dto.bloodPressureDiastolic())
                .temperature(dto.temperature())
                .measurementSite(dto.measurementSite())
                .heartRate(dto.heartRate())
                .oxygenSaturation(dto.oxygenSaturation())
                .respiratoryRate(dto.respiratoryRate())
                .notes(dto.notes())
                .isTriage(dto.isTriage())
                .build();

        try {
            return vitalSignsRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    public Optional<VitalSigns> update(Long id, VitalSignsUpdateDTO dto) {
        Long targetId = id != null ? id : (dto != null ? dto.id() : null);
        LOG.info("[UPDATE] VitalSigns id={} payload={}", targetId, dto);

        if (targetId == null) {
            throw new BadRequestAlertException("VitalSigns id is required", "vitalSigns", "id.required");
        }

        return vitalSignsRepository.findById(targetId).map(entity -> {

            Patient patient = patientRepository.findById(dto.patientId())
                    .orElseThrow(() -> new NotFoundAlertException(
                            "Patient not found with id " + dto.patientId(),
                            "vitalSigns",
                            "patient.notfound"
                    ));

            entity.setPatient(patient);
            entity.setEncounterId(dto.encounterId());

            entity.setBloodPressureSystolic(dto.bloodPressureSystolic());
            entity.setBloodPressureDiastolic(dto.bloodPressureDiastolic());
            entity.setTemperature(dto.temperature());

            entity.setMeasurementSite(dto.measurementSite());
            entity.setHeartRate(dto.heartRate());
            entity.setOxygenSaturation(dto.oxygenSaturation());
            entity.setRespiratoryRate(dto.respiratoryRate());
            entity.setNotes(dto.notes());
            entity.setIsTriage(dto.isTriage());

            try {
                return vitalSignsRepository.saveAndFlush(entity);
            } catch (DataIntegrityViolationException | JpaSystemException ex) {
                throw handleConstraintViolation(ex);
            }
        });
    }

    @Transactional(readOnly = true)
    public Optional<VitalSigns> findLatestByPatientId(Long patientId) {
        LOG.debug("[FIND_LATEST_BY_PATIENT] patientId={}", patientId);
        return vitalSignsRepository.findFirstByPatient_IdOrderByCreatedDateDesc(patientId);
    }

    @Transactional(readOnly = true)
    public Optional<VitalSigns> findLatestByEncounterId(Long encounterId) {
        LOG.debug("[FIND_LATEST_BY_ENCOUNTER] encounterId={}", encounterId);
        return vitalSignsRepository.findFirstByEncounterIdOrderByCreatedDateDesc(encounterId);
    }

    @Transactional(readOnly = true)
    public Optional<VitalSigns> findLatestTriageByEncounterId(Long encounterId) {
        LOG.debug("[FIND_LATEST_TRIAGE_BY_ENCOUNTER] encounterId={}", encounterId);

        return vitalSignsRepository
                .findFirstByEncounterIdAndIsTriageTrueOrderByCreatedDateDesc(encounterId);
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] VitalSigns constraint violated rootMessage={}", message, exception);

        if (messageLower.contains("fk_encounter_vital_signs_patient")
                || messageLower.contains("fk_vital_signs_patient")) {
            return new BadRequestAlertException("Invalid patient id.", "vitalSigns", "patient.invalid");
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving vital signs.",
                "vitalSigns",
                "db.constraint"
        );
    }


}
