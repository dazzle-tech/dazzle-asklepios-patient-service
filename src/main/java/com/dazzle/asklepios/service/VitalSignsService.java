package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.VitalSigns;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import com.dazzle.asklepios.service.dto.vitalSigns.VitalSignsCreateDTO;
import com.dazzle.asklepios.service.dto.vitalSigns.VitalSignsUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;

import com.dazzle.asklepios.web.rest.vm.observations.BloodPressureResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.OxygenSaturationResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.PulseRateResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.RespiratoryRateResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.TemperatureResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.VitalSignsResponseVM;
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
import java.util.List;
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

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "vitalSigns",
                        "patient.notfound"
                ));

        try {
            resetIsActiveForEncounterToday(dto.encounterId());

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
                    .isActive(true)
                    .build();

            return vitalSignsRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    public Optional<VitalSigns> update(Long id, VitalSignsUpdateDTO dto) {
        Long targetId = id != null ? id : dto.id();
        LOG.info("[UPDATE] VitalSigns id={} payload={}", targetId, dto);

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
            entity.setIsActive(dto.isActive());

            try {
                return vitalSignsRepository.saveAndFlush(entity);
            } catch (DataIntegrityViolationException | JpaSystemException ex) {
                throw handleConstraintViolation(ex);
            }
        });
    }

    @Transactional(readOnly = true)
    public Optional<VitalSigns> findLatestByEncounterId(Long encounterId) {
        LOG.debug("[FIND_LATEST_BY_ENCOUNTER] encounterId={}", encounterId);
        return vitalSignsRepository.findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounterId);
    }

    @Transactional(readOnly = true)
    public Optional<VitalSigns> findLatestTriageByEncounterId(Long encounterId) {
        LOG.debug("[FIND_LATEST_TRIAGE_BY_ENCOUNTER] encounterId={}", encounterId);

        return vitalSignsRepository
                .findFirstByEncounterIdAndIsTriageTrueAndIsActiveTrueOrderByCreatedDateDesc(encounterId);
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

    private void resetIsActiveForEncounterToday(Long encounterId) {

        Instant now = Instant.now();
        Instant dayStart = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant dayEnd = dayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);

        LOG.debug(
                "[RESET ACTIVE] Setting latest VitalSigns isActive=false for today, encounterId={}",
                encounterId
        );

        vitalSignsRepository
                .findFirstByEncounterIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateDesc(
                        encounterId,
                        dayStart,
                        dayEnd
                )
                .ifPresentOrElse(vitalSigns -> {
                    vitalSigns.setIsActive(false);
                    vitalSignsRepository.flush();
                    LOG.debug(
                            "[RESET ACTIVE] Reset done. vitalSignsId={} encounterId={}",
                            vitalSigns.getId(),
                            encounterId
                    );
                }, () -> LOG.debug(
                        "[RESET ACTIVE] No active VitalSigns found to reset"
                ));
    }


    @Transactional(readOnly = true)
    public Page<VitalSigns> findVitalSignsByPatientIdBetweenDates(
            Long patientId,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        LOG.debug(
                "[FIND_BY_PATIENT_BETWEEN_DATES] patientId={} from={} to={} pageable={}",
                patientId, from, to, pageable
        );

        patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        "vitalSigns",
                        "patient.notfound"
                ));

        return vitalSignsRepository
                .findByPatientIdAndIsActiveTrueAndCreatedDateBetween(patientId, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public List<VitalSigns> findVitalSignsListByPatientBetweenDates(
            Long patientId,
            Instant from,
            Instant to
    ) {
        LOG.debug("[FIND_VITAL_SIGNS_LIST] patientId={} from={} to={}", patientId, from, to);

        patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        "vitalSigns",
                        "patient.notfound"
                ));

        return vitalSignsRepository
                .findByPatientIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateAsc(patientId, from, to);
    }

}
