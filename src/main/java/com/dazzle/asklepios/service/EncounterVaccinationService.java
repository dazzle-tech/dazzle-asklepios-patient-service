package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.VaccineClient;
import com.dazzle.asklepios.domain.EncounterVaccination;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.EncounterVaccinationStatus;
import com.dazzle.asklepios.repository.EncounterVaccinationRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.projection.EncounterVaccinationProjections;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationCancelDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationCreateDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationReviewDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.EncounterVaccinationUpdateDTO;
import com.dazzle.asklepios.service.dto.encounterVaccination.PatientVaccineDetailsDTO;
import com.dazzle.asklepios.service.helper.VaccineBrandHelper;
import com.dazzle.asklepios.service.helper.VaccineDosesHelper;
import com.dazzle.asklepios.service.helper.VaccineHelper;
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

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class EncounterVaccinationService {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterVaccinationService.class);

    private static final String ENTITY_NAME = "EncounterVaccination";

    private final EncounterVaccinationRepository encounterVaccinationRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final VaccineHelper vaccineHelper;
    private final VaccineBrandHelper vaccineBrandHelper;
    private final VaccineDosesHelper vaccineDosesHelper;

    public EncounterVaccination create(EncounterVaccinationCreateDTO createRequest) {
        LOG.info("[CREATE] EncounterVaccination payload={}", createRequest);

        Patient patient = patientRepository.findById(createRequest.patientId())
                .orElseThrow(() -> {
                    LOG.warn("[CREATE] EncounterVaccination rejected: patient not found patientId={}", createRequest.patientId());
                    return new NotFoundAlertException(
                            "Patient not found with id " + createRequest.patientId(),
                            "encounterVaccination",
                            "patient.notfound"
                    );
                });

        loadEncounter(createRequest.encounterId());
        vaccineHelper.validateVaccineExists(createRequest.vaccineId());
        vaccineBrandHelper.validateVaccineBrandExists(createRequest.vaccineBrandId());
        vaccineDosesHelper.validateVaccineDosesExists(createRequest.vaccineDoseId());

        String normalizedExternalFacilityName = Boolean.TRUE.equals(createRequest.isExternalFacility())
                ? createRequest.externalFacilityName()
                : null;

        EncounterVaccination encounterVaccination = EncounterVaccination.builder()
                .patient(patient)
                .encounterId(createRequest.encounterId())
                .vaccineId(createRequest.vaccineId())
                .vaccineBrandId(createRequest.vaccineBrandId())
                .vaccineDoseId(createRequest.vaccineDoseId())
                .vaccineLotNumber(createRequest.vaccineLotNumber())
                .dateAdministered(createRequest.dateAdministered())
                .status(createRequest.status())
                .cancellationReason(createRequest.cancellationReason())
                .administeredLocation(createRequest.administeredLocation())
                .administrationReactions(createRequest.administrationReactions())
                .isExternalFacility(createRequest.isExternalFacility())
                .externalFacilityName(normalizedExternalFacilityName)
                .notes(createRequest.notes())
                .build();

        try {
            EncounterVaccination savedEncounterVaccination = encounterVaccinationRepository.saveAndFlush(encounterVaccination);
            LOG.info("[CREATE] EncounterVaccination success id={} patientId={} encounterId={} vaccineId={} doseId={} status={}",
                    savedEncounterVaccination.getId(),
                    createRequest.patientId(),
                    createRequest.encounterId(),
                    createRequest.vaccineId(),
                    createRequest.vaccineDoseId(),
                    createRequest.status()
            );
            return savedEncounterVaccination;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CREATE] EncounterVaccination failed (constraint) payload={}", createRequest, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[CREATE] EncounterVaccination failed (unexpected) payload={}", createRequest, ex);
            throw ex;
        }
    }

    public EncounterVaccination update(Long encounterVaccinationId, EncounterVaccinationUpdateDTO updateRequest) {
        LOG.info("[UPDATE] EncounterVaccination id={} payload={}", encounterVaccinationId, updateRequest);

        return encounterVaccinationRepository.findById(encounterVaccinationId)
                .map(encounterVaccination -> {

                    Patient patient = patientRepository.findById(updateRequest.patientId())
                            .orElseThrow(() -> {
                                LOG.warn(
                                        "[UPDATE] EncounterVaccination rejected: patient not found patientId={} id={}",
                                        updateRequest.patientId(), encounterVaccinationId
                                );
                                return new NotFoundAlertException(
                                        "Patient not found with id " + updateRequest.patientId(),
                                        "encounterVaccination",
                                        "patient.notfound"
                                );
                            });

                    loadEncounter(updateRequest.encounterId());
                    vaccineHelper.validateVaccineExists(updateRequest.vaccineId());
                    vaccineBrandHelper.validateVaccineBrandExists(updateRequest.vaccineBrandId());
                    vaccineDosesHelper.validateVaccineDosesExists(updateRequest.vaccineDoseId());

                    encounterVaccination.setPatient(patient);
                    encounterVaccination.setEncounterId(updateRequest.encounterId());
                    encounterVaccination.setVaccineId(updateRequest.vaccineId());
                    encounterVaccination.setVaccineBrandId(updateRequest.vaccineBrandId());
                    encounterVaccination.setVaccineDoseId(updateRequest.vaccineDoseId());
                    encounterVaccination.setVaccineLotNumber(updateRequest.vaccineLotNumber());
                    encounterVaccination.setDateAdministered(updateRequest.dateAdministered());
                    encounterVaccination.setStatus(updateRequest.status());
                    encounterVaccination.setAdministeredLocation(updateRequest.administeredLocation());
                    encounterVaccination.setAdministrationReactions(updateRequest.administrationReactions());
                    encounterVaccination.setIsExternalFacility(updateRequest.isExternalFacility());
                    encounterVaccination.setExternalFacilityName(Boolean.TRUE.equals(updateRequest.isExternalFacility())
                            ? updateRequest.externalFacilityName()
                            : null);

                    encounterVaccination.setNotes(updateRequest.notes());

                    try {
                        EncounterVaccination savedEncounterVaccination =
                                encounterVaccinationRepository.saveAndFlush(encounterVaccination);

                        LOG.info(
                                "[UPDATE] EncounterVaccination success id={} patientId={} encounterId={} vaccineId={} doseId={} status={}",
                                savedEncounterVaccination.getId(),
                                updateRequest.patientId(),
                                updateRequest.encounterId(),
                                updateRequest.vaccineId(),
                                updateRequest.vaccineDoseId(),
                                updateRequest.status()
                        );

                        return savedEncounterVaccination;
                    } catch (DataIntegrityViolationException | JpaSystemException ex) {
                        LOG.warn(
                                "[UPDATE] EncounterVaccination failed (constraint) id={} payload={}",
                                encounterVaccinationId, updateRequest, ex
                        );
                        throw handleConstraintViolation(ex);
                    } catch (RuntimeException ex) {
                        LOG.error(
                                "[UPDATE] EncounterVaccination failed (unexpected) id={} payload={}",
                                encounterVaccinationId, updateRequest, ex
                        );
                        throw ex;
                    }
                })
                .orElseThrow(() -> {
                    LOG.warn("[UPDATE] EncounterVaccination rejected: not found id={}", encounterVaccinationId);
                    return new NotFoundAlertException(
                            "EncounterVaccination not found with id " + encounterVaccinationId,
                            "encounterVaccination",
                            "id.notfound"
                    );
                });
    }

    public EncounterVaccination cancel(EncounterVaccinationCancelDTO cancelRequest) {

        String currentUser = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "Current user not found",
                        "encounterVaccination",
                        "user.notfound"
                ));

        LOG.info(
                "[CANCEL] EncounterVaccination id={} cancelledBy={} reason={}",
                cancelRequest.id(), currentUser, cancelRequest.cancellationReason()
        );

        EncounterVaccination encounterVaccination = encounterVaccinationRepository.findById(cancelRequest.id())
                .orElseThrow(() -> {
                    LOG.warn("[CANCEL] EncounterVaccination not found id={}", cancelRequest.id());
                    return new NotFoundAlertException(
                            "EncounterVaccination not found with id " + cancelRequest.id(),
                            "encounterVaccination",
                            "notfound"
                    );
                });

        encounterVaccination.setStatus(EncounterVaccinationStatus.CANCELLED);
        encounterVaccination.setCancellationReason(cancelRequest.cancellationReason());
        encounterVaccination.setCancelledAt(Instant.now());
        encounterVaccination.setCancelledBy(currentUser);

        try {
            EncounterVaccination savedEncounterVaccination = encounterVaccinationRepository.saveAndFlush(encounterVaccination);
            LOG.info("[CANCEL] EncounterVaccination success id={} cancelledBy={}",
                    cancelRequest.id(), currentUser);
            return savedEncounterVaccination;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CANCEL] EncounterVaccination failed (constraint) id={} cancelledBy={}",
                    cancelRequest.id(), currentUser, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[CANCEL] EncounterVaccination failed (unexpected) id={}", cancelRequest.id(), ex);
            throw ex;
        }
    }
    public EncounterVaccination review(EncounterVaccinationReviewDTO reviewRequest) {

        String currentUser = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "Current user not found",
                        "encounterVaccination",
                        "user.notfound"
                ));

        LOG.info("[REVIEW] EncounterVaccination id={} reviewedBy={}",
                reviewRequest.id(), currentUser);

        EncounterVaccination encounterVaccination = encounterVaccinationRepository.findById(reviewRequest.id())
                .orElseThrow(() -> {
                    LOG.warn("[REVIEW] EncounterVaccination not found id={}", reviewRequest.id());
                    return new NotFoundAlertException(
                            "EncounterVaccination not found with id " + reviewRequest.id(),
                            "encounterVaccination",
                            "notfound"
                    );
                });

        encounterVaccination.setStatus(EncounterVaccinationStatus.REVIEW);
        encounterVaccination.setReviewedById(currentUser);
        encounterVaccination.setReviewedAt(Instant.now());

        try {
            EncounterVaccination savedEncounterVaccination = encounterVaccinationRepository.saveAndFlush(encounterVaccination);
            LOG.info("[REVIEW] EncounterVaccination success id={} reviewedBy={}",
                    reviewRequest.id(), currentUser);
            return savedEncounterVaccination;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[REVIEW] EncounterVaccination failed (constraint) id={} reviewedBy={}",
                    reviewRequest.id(), currentUser, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[REVIEW] EncounterVaccination failed (unexpected) id={}", reviewRequest.id(), ex);
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
        LOG.debug("[FIND_ALL_BY_ENCOUNTER] encounterId={} pageable={}",
                encounterId, pageable);

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
    public Page<EncounterVaccination> findPatientVaccinationsAll(Long patientId, Pageable pageable) {
        LOG.debug("[FIND_ALL_BY_PATIENT] patientId={} pageable={}", patientId, pageable);

        return encounterVaccinationRepository.findByPatient_Id(
                patientId,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public EncounterVaccination getById(Long encounterVaccinationId) {
        LOG.debug("[GET_BY_ID] EncounterVaccination id={}", encounterVaccinationId);

        return encounterVaccinationRepository.findById(encounterVaccinationId)
                .orElseThrow(() -> {
                    LOG.warn("[GET_BY_ID] EncounterVaccination not found id={}", encounterVaccinationId);
                    return new NotFoundAlertException(
                            "EncounterVaccination not found with id " + encounterVaccinationId,
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
                    .toList();

            doseIds = encounterVaccinationRepository
                    .findDistinctDoseIdsByPatient_IdAndVaccineId(patientId, vaccineId)
                    .stream()
                    .map(EncounterVaccinationProjections.VaccineDoseIdView::getVaccineDoseId)
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
                    .toList();

            doseIds = encounterVaccinationRepository
                    .findDistinctDoseIdsByPatient_IdAndVaccineIdAndStatusNot(
                            patientId,
                            vaccineId,
                            EncounterVaccinationStatus.CANCELLED
                    )
                    .stream()
                    .map(EncounterVaccinationProjections.VaccineDoseIdView::getVaccineDoseId)
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


    private PatientEncounter loadEncounter(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "encounter.notfound",
                        ENTITY_NAME,
                        "Encounter not found with id " + encounterId
                ));
    }

    @Transactional(readOnly = true)
    public List<Long> findPatientVaccineIds(Long patientId) {
        LOG.debug("[FIND_PATIENT_VACCINE_IDS] patientId={}", patientId);

        return encounterVaccinationRepository
                .findDistinctByPatient_Id(patientId)
                .stream()
                .map(EncounterVaccinationProjections.VaccineIdView::getVaccineId)
                .toList();
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] EncounterVaccination constraint violated rootMessage={}", message, exception);

        if (messageLower.contains("ux_enc_vacc_patient_dose_active")) {
            LOG.warn("[DB_CONSTRAINT] Duplicate active patient/dose violated (partial unique index)");
            return new BadRequestAlertException(
                    "This patient already has the same vaccine dose recorded (non-cancelled).",
                    "encounterVaccination",
                    "patient.dose.duplicate.active"
            );
        }

        if (messageLower.contains("ck_encounter_vaccination_external_facility_name")) {
            return new BadRequestAlertException(
                    "External facility name is required when isExternalFacility is true.",
                    "encounterVaccination",
                    "externalFacilityName.required"
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