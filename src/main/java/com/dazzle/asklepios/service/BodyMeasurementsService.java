package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BodyMeasurements;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.bodyMeasurements.BodyMeasurementsCreateDTO;
import com.dazzle.asklepios.service.dto.bodyMeasurements.BodyMeasurementsUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;

import com.dazzle.asklepios.web.rest.vm.observations.BodyMeasurementsResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.HeightResponseVM;
import com.dazzle.asklepios.web.rest.vm.observations.WeightResponseVM;
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

        try {
            resetIsActiveForEncounterToday(dto.encounterId());

            BodyMeasurements entity = BodyMeasurements.builder()
                    .patient(patient)
                    .encounterId(dto.encounterId())
                    .weight(dto.weight())
                    .height(dto.height())
                    .headCircumference(dto.headCircumference())
                    .isActive(true)
                    .build();

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
    public Page<BodyMeasurementsResponseVM> findBodyMeasurementsVmByPatientBetweenDates(
            Long patientId,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        LOG.debug("[FIND_BODY_MEASUREMENTS_VM_PAGE] patientId={} from={} to={} pageable={}", patientId, from, to, pageable);

        patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        "bodyMeasurements",
                        "patient.notfound"
                ));

        return bodyMeasurementsRepository
                .findByPatientIdAndIsActiveTrueAndCreatedDateBetween(patientId, from, to, pageable)
                .map(bm -> BodyMeasurementsResponseVM.builder()
                        .weight(bm.getWeight())
                        .height(bm.getHeight())
                        .createdAt(bm.getCreatedDate())
                        .build()
                );

    }
        @Transactional(readOnly = true)
    public List<WeightResponseVM> findWeightByPatientBetweenDates(Long patientId, Instant from, Instant to) {
        LOG.debug("[FIND_WEIGHT_LIST] patientId={} from={} to={}", patientId, from, to);
        validatePatient(patientId);

        return bodyMeasurementsRepository
                .findByPatientIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateAsc(patientId, from, to)
                .stream()
                .map(bm -> WeightResponseVM.builder()
                        .weight(bm.getWeight())
                        .createdAt(bm.getCreatedDate())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HeightResponseVM> findHeightByPatientBetweenDates(Long patientId, Instant from, Instant to) {
        LOG.debug("[FIND_HEIGHT_LIST] patientId={} from={} to={}", patientId, from, to);
        validatePatient(patientId);

        return bodyMeasurementsRepository
                .findByPatientIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateAsc(patientId, from, to)
                .stream()
                .map(bm -> HeightResponseVM.builder()
                        .height(bm.getHeight())
                        .createdAt(bm.getCreatedDate())
                        .build())
                .toList();
    }

    private void validatePatient(Long patientId) {
        patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + patientId,
                        "bodyMeasurements",
                        "patient.notfound"
                ));
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

    private void resetIsActiveForEncounterToday(Long encounterId) {

        Instant now = Instant.now();
        Instant dayStart = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant dayEnd = dayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);

        LOG.debug(
                "[RESET ACTIVE] Setting latest BodyMeasurements isActive=false for today, encounterId={}",
                encounterId
        );

        bodyMeasurementsRepository
                .findFirstByEncounterIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateDesc(
                        encounterId,
                        dayStart,
                        dayEnd
                )
                .ifPresentOrElse(bodyMeasurements -> {
                    bodyMeasurements.setIsActive(false);
                    bodyMeasurementsRepository.flush();
                    LOG.debug(
                            "[RESET ACTIVE] Reset done. bodyMeasurementsId={} encounterId={}",
                            bodyMeasurements.getId(),
                            encounterId
                    );
                }, () -> LOG.debug(
                        "[RESET ACTIVE] No active BodyMeasurements found to reset"
                ));
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
