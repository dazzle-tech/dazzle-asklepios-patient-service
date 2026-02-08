package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.EncounterVaccination;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.EncounterVaccinationStatus;
import com.dazzle.asklepios.repository.EncounterVaccinationRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.projection.EncounterVaccinationProjections;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationCancelDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationCreateDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationReviewDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationUpdateDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.PatientVaccineDetailsDTO;
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
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class EncounterVaccinationService {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterVaccinationService.class);

    private final EncounterVaccinationRepository encounterVaccinationRepository;
    private final PatientRepository patientRepository;

    public EncounterVaccination create(EncounterVaccinationCreateDTO dto) {
        LOG.info("[CREATE] EncounterVaccination payload={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> {
                    LOG.warn("[CREATE] EncounterVaccination rejected: patient not found patientId={}", dto.patientId());
                    return new NotFoundAlertException(
                            "Patient not found with id " + dto.patientId(),
                            "encounterVaccination",
                            "patient.notfound"
                    );
                });

        EncounterVaccination entity = EncounterVaccination.builder()
                .patient(patient)
                .encounterId(dto.encounterId())
                .vaccineId(dto.vaccineId())
                .vaccineBrandId(dto.vaccineBrandId())
                .vaccineDoseId(dto.vaccineDoseId())
                .vaccineLotNumber(dto.vaccineLotNumber())
                .dateAdministered(dto.dateAdministered())
                .status(dto.status())
                .cancellationReason(dto.cancellationReason())
                .cancelledAt(dto.cancelledAt())
                .cancelledById(dto.cancelledById())
                .administeredLocation(dto.administeredLocation())
                .administrationReactions(dto.administrationReactions())
                .externalFacilityName(dto.externalFacilityName())
                .notes(dto.notes())
                .reviewedAt(dto.reviewedAt())
                .reviewedById(dto.reviewedById())
                .build();

        try {
            EncounterVaccination saved = encounterVaccinationRepository.saveAndFlush(entity);
            LOG.info("[CREATE] EncounterVaccination success id={} patientId={} encounterId={} vaccineId={} doseId={} status={}",
                    saved.getId(), dto.patientId(), dto.encounterId(), dto.vaccineId(), dto.vaccineDoseId(), dto.status());
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CREATE] EncounterVaccination failed (constraint) payload={}", dto, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[CREATE] EncounterVaccination failed (unexpected) payload={}", dto, ex);
            throw ex;
        }
    }

    public Optional<EncounterVaccination> update(Long id, EncounterVaccinationUpdateDTO dto) {
        LOG.info("[UPDATE] EncounterVaccination id={} payload={}", id, dto);
        return encounterVaccinationRepository.findById(id).map(entity -> {

            Patient patient = patientRepository.findById(dto.patientId())
                    .orElseThrow(() -> {
                        LOG.warn("[UPDATE] EncounterVaccination rejected: patient not found patientId={} id={}", dto.patientId(), id);
                        return new NotFoundAlertException(
                                "Patient not found with id " + dto.patientId(),
                                "encounterVaccination",
                                "patient.notfound"
                        );
                    });

            entity.setPatient(patient);
            entity.setEncounterId(dto.encounterId());
            entity.setVaccineId(dto.vaccineId());
            entity.setVaccineBrandId(dto.vaccineBrandId());
            entity.setVaccineDoseId(dto.vaccineDoseId());
            entity.setVaccineLotNumber(dto.vaccineLotNumber());
            entity.setDateAdministered(dto.dateAdministered()); // Instant
            entity.setStatus(dto.status());
            entity.setCancellationReason(dto.cancellationReason());

            entity.setCancelledAt(dto.cancelledAt());
            entity.setCancelledById(dto.cancelledById());

            entity.setAdministeredLocation(dto.administeredLocation());
            entity.setAdministrationReactions(dto.administrationReactions());
            entity.setExternalFacilityName(dto.externalFacilityName());
            entity.setNotes(dto.notes());

            entity.setReviewedAt(dto.reviewedAt());
            entity.setReviewedById(dto.reviewedById());

            try {
                EncounterVaccination saved = encounterVaccinationRepository.saveAndFlush(entity);
                LOG.info("[UPDATE] EncounterVaccination success id={} patientId={} encounterId={} vaccineId={} doseId={} status={}",
                        saved.getId(), dto.patientId(), dto.encounterId(), dto.vaccineId(), dto.vaccineDoseId(), dto.status());
                return saved;
            } catch (DataIntegrityViolationException | JpaSystemException ex) {
                LOG.warn("[UPDATE] EncounterVaccination failed (constraint) id={} payload={}", id, dto, ex);
                throw handleConstraintViolation(ex);
            } catch (RuntimeException ex) {
                LOG.error("[UPDATE] EncounterVaccination failed (unexpected) id={} payload={}", id, dto, ex);
                throw ex;
            }
        });
    }

    public EncounterVaccination cancel(EncounterVaccinationCancelDTO dto) {
        LOG.info(
                "[CANCEL] EncounterVaccination id={} cancelledById={} reason={}",
                dto.id(), dto.cancelledById(), dto.cancellationReason()
        );

        EncounterVaccination entity = encounterVaccinationRepository.findById(dto.id())
                .orElseThrow(() -> {
                    LOG.warn("[CANCEL] EncounterVaccination not found id={}", dto.id());
                    return new NotFoundAlertException(
                            "EncounterVaccination not found with id " + dto.id(),
                            "encounterVaccination",
                            "notfound"
                    );
                });

        entity.setStatus(EncounterVaccinationStatus.CANCELLED);
        entity.setCancellationReason(dto.cancellationReason());
        entity.setCancelledAt(Instant.now());
        entity.setCancelledById(dto.cancelledById());

        try {
            EncounterVaccination saved = encounterVaccinationRepository.saveAndFlush(entity);
            LOG.info("[CANCEL] EncounterVaccination success id={} cancelledById={}",
                    dto.id(), dto.cancelledById());
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CANCEL] EncounterVaccination failed (constraint) id={} cancelledById={}",
                    dto.id(), dto.cancelledById(), ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[CANCEL] EncounterVaccination failed (unexpected) id={}", dto.id(), ex);
            throw ex;
        }
    }

    public EncounterVaccination review(EncounterVaccinationReviewDTO dto) {
        LOG.info("[REVIEW] EncounterVaccination id={} reviewedById={}",
                dto.id(), dto.reviewedById());

        EncounterVaccination entity = encounterVaccinationRepository.findById(dto.id())
                .orElseThrow(() -> {
                    LOG.warn("[REVIEW] EncounterVaccination not found id={}", dto.id());
                    return new NotFoundAlertException(
                            "EncounterVaccination not found with id " + dto.id(),
                            "encounterVaccination",
                            "notfound"
                    );
                });

        entity.setStatus(EncounterVaccinationStatus.REVIEW);
        entity.setReviewedById(dto.reviewedById());
        entity.setReviewedAt(Instant.now());

        try {
            EncounterVaccination saved = encounterVaccinationRepository.saveAndFlush(entity);
            LOG.info("[REVIEW] EncounterVaccination success id={} reviewedById={}",
                    dto.id(), dto.reviewedById());
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[REVIEW] EncounterVaccination failed (constraint) id={} reviewedById={}",
                    dto.id(), dto.reviewedById(), ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[REVIEW] EncounterVaccination failed (unexpected) id={}", dto.id(), ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<EncounterVaccination> findEncounterVaccinationsActive(Long encounterId, Pageable pageable) {
        LOG.debug("[FIND_ACTIVE_BY_ENCOUNTER] encounterId={} pageable={}", encounterId, pageable);

        return encounterVaccinationRepository.findByEncounterIdAndStatusNot(
                encounterId,
                EncounterVaccinationStatus.CANCELLED,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public Page<EncounterVaccination> findEncounterVaccinationsAll(Long encounterId, Pageable pageable) {
        LOG.debug(
                "[FIND_ALL_BY_ENCOUNTER] encounterId={} pageable={}",
                encounterId, pageable
        );

        return encounterVaccinationRepository.findByEncounterId(
                encounterId,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public Page<EncounterVaccination> findPatientVaccinationsActive(Long patientId, Pageable pageable) {
        LOG.debug("[FIND_ACTIVE_BY_PATIENT] patientId={} pageable={}", patientId, pageable);

        return encounterVaccinationRepository.findByPatient_IdAndStatusNot(
                patientId,
                EncounterVaccinationStatus.CANCELLED,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public Page<EncounterVaccination> findPatientVaccinationsAll(
            Long patientId,
            Pageable pageable
    ) {
        LOG.debug(
                "[FIND_ALL_BY_PATIENT] patientId={} pageable={}",
                patientId, pageable
        );

        return encounterVaccinationRepository.findByPatient_Id(
                patientId,
                pageable
        );
    }


    @Transactional(readOnly = true)
    public EncounterVaccination getById(Long id) {
        LOG.debug("[GET_BY_ID] EncounterVaccination id={}", id);

        return encounterVaccinationRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.warn("[GET_BY_ID] EncounterVaccination not found id={}", id);
                    return new NotFoundAlertException(
                            "EncounterVaccination not found with id " + id,
                            "encounterVaccination",
                            "notfound"
                    );
                });
    }

    @Transactional(readOnly = true)
    public PatientVaccineDetailsDTO findPatientVaccineDetails(
            Long patientId,
            Long vaccineId,
            boolean includeCancelled,
            Pageable pageable
    ) {
        LOG.debug(
                "[FIND_PATIENT_VACCINE_DETAILS] patientId={} vaccineId={} includeCancelled={} pageable={}",
                patientId, vaccineId, includeCancelled, pageable
        );

        final Page<EncounterVaccination> records;
        final List<Long> brandIds;
        final List<Long> doseIds;

        if (includeCancelled) {
            LOG.debug("[FIND_PATIENT_VACCINE_DETAILS] Including CANCELLED records");

            records = encounterVaccinationRepository
                    .findByPatient_IdAndVaccineId(patientId, vaccineId, pageable);

            brandIds = encounterVaccinationRepository
                    .findDistinctBrandIdsByPatient_IdAndVaccineId(patientId, vaccineId)
                    .stream()
                    .map(EncounterVaccinationProjections.VaccineBrandIdView::getVaccineBrandId)
                    .distinct()
                    .toList();

            doseIds = encounterVaccinationRepository
                    .findDistinctDoseIdsByPatient_IdAndVaccineId(patientId, vaccineId)
                    .stream()
                    .map(EncounterVaccinationProjections.VaccineDoseIdView::getVaccineDoseId)
                    .distinct()
                    .toList();

        } else {
            LOG.debug("[FIND_PATIENT_VACCINE_DETAILS] Excluding CANCELLED records");

            records = encounterVaccinationRepository
                    .findByPatient_IdAndVaccineIdAndStatusNot(
                            patientId,
                            vaccineId,
                            EncounterVaccinationStatus.CANCELLED,
                            pageable
                    );

            brandIds = encounterVaccinationRepository
                    .findDistinctBrandIdsByPatient_IdAndVaccineIdAndStatusNot(
                            patientId,
                            vaccineId,
                            EncounterVaccinationStatus.CANCELLED
                    )
                    .stream()
                    .map(EncounterVaccinationProjections.VaccineBrandIdView::getVaccineBrandId)
                    .distinct()
                    .toList();

            doseIds = encounterVaccinationRepository
                    .findDistinctDoseIdsByPatient_IdAndVaccineIdAndStatusNot(
                            patientId,
                            vaccineId,
                            EncounterVaccinationStatus.CANCELLED
                    )
                    .stream()
                    .map(EncounterVaccinationProjections.VaccineDoseIdView::getVaccineDoseId)
                    .distinct()
                    .toList();
        }

        LOG.debug(
                "[FIND_PATIENT_VACCINE_DETAILS] Result summary patientId={} vaccineId={} records={} brands={} doses={}",
                patientId,
                vaccineId,
                records.getTotalElements(),
                brandIds.size(),
                doseIds.size()
        );

        return new PatientVaccineDetailsDTO(records, brandIds, doseIds);
    }

    @Transactional(readOnly = true)
    public List<Long> findPatientVaccineIds(Long patientId) {
        LOG.debug("[FIND_PATIENT_VACCINE_IDS] patientId={}", patientId);

        List<Long> vaccineIds = encounterVaccinationRepository
                .findDistinctByPatient_Id(patientId)
                .stream()
                .map(EncounterVaccinationProjections.VaccineIdView::getVaccineId)
                .toList();

        LOG.debug(
                "[FIND_PATIENT_VACCINE_IDS] patientId={} vaccineIdsCount={}",
                patientId,
                vaccineIds.size()
        );

        return vaccineIds;
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] EncounterVaccination constraint violated rootMessage={}", message, exception);

        if (messageLower.contains("ux_encounter_vaccination_patient_vaccine_dose_active")) {
            LOG.warn("[DB_CONSTRAINT] Duplicate active patient/vaccine/dose violated (partial unique index)");
            return new BadRequestAlertException(
                    "This patient already has the same vaccine and dose recorded (non-cancelled).",
                    "encounterVaccination",
                    "patient.vaccine.dose.duplicate.active"
            );
        }

        if (messageLower.contains("ix_encounter_vaccination_patient_vaccine_dose")
                || messageLower.contains("encounter_vaccination_patient_id_vaccine_id_vaccine_dose_id_key")
                || messageLower.contains("unique")
                && messageLower.contains("patient_id")
                && messageLower.contains("vaccine_id")
                && messageLower.contains("vaccine_dose_id")) {
            LOG.warn("[DB_CONSTRAINT] Duplicate patient/vaccine/dose violated (unique index)");
            return new BadRequestAlertException(
                    "This patient already has the same vaccine and dose recorded.",
                    "encounterVaccination",
                    "patient.vaccine.dose.duplicate"
            );
        }
        if (messageLower.contains("ck_encounter_vaccination_cancellation_reason")) {
            return new BadRequestAlertException(
                    "Cancellation reason is required when status is CANCELLED.",
                    "encounterVaccination",
                    "cancellationReason.required"
            );
        }

        if (messageLower.contains("ck_encounter_vaccination_cancelled_at")) {
            return new BadRequestAlertException(
                    "CancelledAt is required when status is CANCELLED.",
                    "encounterVaccination",
                    "cancelledAt.required"
            );
        }

        if (messageLower.contains("ck_encounter_vaccination_cancelled_by")) {
            return new BadRequestAlertException(
                    "CancelledById is required when status is CANCELLED.",
                    "encounterVaccination",
                    "cancelledBy.required"
            );
        }

        if (messageLower.contains("ck_encounter_vaccination_reviewed_at")) {
            return new BadRequestAlertException(
                    "ReviewedAt is required when status is REVIEW.",
                    "encounterVaccination",
                    "reviewedAt.required"
            );
        }

        if (messageLower.contains("ck_encounter_vaccination_reviewed_by")) {
            return new BadRequestAlertException(
                    "ReviewedById is required when status is REVIEW.",
                    "encounterVaccination",
                    "reviewedBy.required"
            );
        }

        if (messageLower.contains("fk_encounter_vaccination_patient")) {
            return new BadRequestAlertException(
                    "Invalid patient id.",
                    "encounterVaccination",
                    "patient.invalid"
            );
        }
        if (messageLower.contains("fk_encounter_vaccination_vaccine")) {
            return new BadRequestAlertException(
                    "Invalid vaccine id.",
                    "encounterVaccination",
                    "vaccine.invalid"
            );
        }
        if (messageLower.contains("fk_encounter_vaccination_vaccine_dose")) {
            return new BadRequestAlertException(
                    "Invalid vaccine dose id.",
                    "encounterVaccination",
                    "dose.invalid"
            );
        }
        if (messageLower.contains("fk_encounter_vaccination_vaccine_brand")) {
            return new BadRequestAlertException(
                    "Invalid vaccine brand id.",
                    "encounterVaccination",
                    "brand.invalid"
            );
        }
        if (messageLower.contains("fk_encounter_vaccination_cancelled_by")) {
            return new BadRequestAlertException(
                    "Invalid cancelledById (user not found).",
                    "encounterVaccination",
                    "cancelledBy.invalid"
            );
        }
        if (messageLower.contains("fk_encounter_vaccination_reviewed_by")) {
            return new BadRequestAlertException(
                    "Invalid reviewedById (user not found).",
                    "encounterVaccination",
                    "reviewedBy.invalid"
            );
        }

        LOG.warn("[DB_CONSTRAINT] EncounterVaccination unknown constraint violated");
        return new BadRequestAlertException(
                "Database constraint violated while saving encounter vaccination.",
                "encounterVaccination",
                "db.constraint"
        );
    }

}
