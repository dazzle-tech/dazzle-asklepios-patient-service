package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.EncounterAssignToBed;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.BedTransactionType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.repository.EncounterAssignToBedRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.bedTransaction.BedTransactionCreateDTO;
import com.dazzle.asklepios.service.dto.encounterAssignToBed.EncounterAssignToBedCreateDTO;
import com.dazzle.asklepios.service.dto.encounterAssignToBed.EncounterAssignToBedUpdateDTO;
import com.dazzle.asklepios.service.helper.BedHelper;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.RoomHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
public class EncounterAssignToBedService {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterAssignToBedService.class);

    private final EncounterAssignToBedRepository encounterAssignToBedRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientRepository patientRepository;
    private final BedTransactionService bedTransactionService;
    private final BedHelper bedHelper;
    private final RoomHelper roomHelper;
    private final DepartmentHelper departmentHelper;

    public EncounterAssignToBed create(EncounterAssignToBedCreateDTO createDTO) {
        LOG.info("[CREATE] EncounterAssignToBed payload={}", createDTO);

        PatientEncounter patientEncounter = patientEncounterRepository.findById(createDTO.encounterId())
                .orElseThrow(() -> {
                    LOG.warn("[CREATE] EncounterAssignToBed rejected: encounter not found encounterId={}", createDTO.encounterId());
                    return new NotFoundAlertException(
                            "Encounter not found with id " + createDTO.encounterId(),
                            "encounterAssignToBed",
                            "encounter.notfound"
                    );
                });

        Patient patient = patientRepository.findById(createDTO.patientId())
                .orElseThrow(() -> {
                    LOG.warn("[CREATE] EncounterAssignToBed rejected: patient not found patientId={}", createDTO.patientId());
                    return new NotFoundAlertException(
                            "Patient not found with id " + createDTO.patientId(),
                            "encounterAssignToBed",
                            "patient.notfound"
                    );
                });

        validatePatientMatchesEncounter(patientEncounter, patient);
        bedHelper.validateBedExists(createDTO.bedId());
        roomHelper.validateRoomExists(createDTO.roomId());
        validateBedAvailabilityForCreate(createDTO.bedId());
        validateEncounterHasNoActiveAssignment(createDTO.encounterId());
        departmentHelper.validateDepartmentExists(createDTO.departmentId());

        Instant assignmentTime = Instant.now();

        EncounterAssignToBed encounterAssignToBedToCreate = EncounterAssignToBed.builder()
                .encounter(patientEncounter)
                .patient(patient)
                .roomId(createDTO.roomId())
                .bedId(createDTO.bedId())
                .departmentId(createDTO.departmentId())
                .admissionReason(createDTO.admissionReason())
                .assignedAt(assignmentTime)
                .releasedAt(null)
                .isActive(true)
                .build();

        try {
            EncounterAssignToBed createdEncounterAssignToBed =
                    encounterAssignToBedRepository.saveAndFlush(encounterAssignToBedToCreate);
            patientEncounter.setStatus(TreatmentStatus.ASSIGNED_TO_BED);
            patientEncounterRepository.saveAndFlush(patientEncounter);
            bedTransactionService.create(new BedTransactionCreateDTO(
                    createDTO.encounterId(),
                    createDTO.patientId(),
                    null,
                    null,
                    createDTO.roomId(),
                    createDTO.bedId(),
                    createDTO.departmentId(),
                    createDTO.departmentId(),
                    false,
                    BedTransactionType.ASSIGN
            ));

            LOG.info("[CREATE] EncounterAssignToBed success id={} encounterId={} patientId={} roomId={} bedId={}",
                    createdEncounterAssignToBed.getId(),
                    createDTO.encounterId(),
                    createDTO.patientId(),
                    createDTO.roomId(),
                    createDTO.bedId());

            return createdEncounterAssignToBed;
        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            LOG.warn("[CREATE] EncounterAssignToBed failed (constraint) payload={}", createDTO, exception);
            throw handleConstraintViolation(exception);
        } catch (RuntimeException exception) {
            LOG.error("[CREATE] EncounterAssignToBed failed (unexpected) payload={}", createDTO, exception);
            throw exception;
        }
    }

    public EncounterAssignToBed update(Long encounterAssignToBedId, EncounterAssignToBedUpdateDTO updateDTO) {
        LOG.info("[UPDATE] EncounterAssignToBed id={} payload={}", encounterAssignToBedId, updateDTO);

        if (!encounterAssignToBedId.equals(updateDTO.id())) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id.",
                    "encounterAssignToBed",
                    "id.mismatch"
            );
        }

        EncounterAssignToBed existingEncounterAssignToBed = encounterAssignToBedRepository.findById(encounterAssignToBedId)
                .orElseThrow(() -> {
                    LOG.warn("[UPDATE] EncounterAssignToBed rejected: not found id={}", encounterAssignToBedId);
                    return new NotFoundAlertException(
                            "EncounterAssignToBed not found with id " + encounterAssignToBedId,
                            "encounterAssignToBed",
                            "id.notfound"
                    );
                });

        PatientEncounter patientEncounter = patientEncounterRepository.findById(updateDTO.encounterId())
                .orElseThrow(() -> {
                    LOG.warn("[UPDATE] EncounterAssignToBed rejected: encounter not found encounterId={}", updateDTO.encounterId());
                    return new NotFoundAlertException(
                            "Encounter not found with id " + updateDTO.encounterId(),
                            "encounterAssignToBed",
                            "encounter.notfound"
                    );
                });

        Patient patient = patientRepository.findById(updateDTO.patientId())
                .orElseThrow(() -> {
                    LOG.warn("[UPDATE] EncounterAssignToBed rejected: patient not found patientId={}", updateDTO.patientId());
                    return new NotFoundAlertException(
                            "Patient not found with id " + updateDTO.patientId(),
                            "encounterAssignToBed",
                            "patient.notfound"
                    );
                });

        validatePatientMatchesEncounter(patientEncounter, patient);

        EncounterAssignToBed currentActiveEncounterAssignToBed =
                encounterAssignToBedRepository.findByEncounter_IdAndIsActiveTrue(updateDTO.encounterId())
                        .orElseThrow(() -> {
                            LOG.warn("[UPDATE] EncounterAssignToBed rejected: no active assignment found for encounterId={}",
                                    updateDTO.encounterId());
                            return new NotFoundAlertException(
                                    "Active assignment not found for encounter id " + updateDTO.encounterId(),
                                    "encounterAssignToBed",
                                    "activeAssignment.notfound"
                            );
                        });

        if (!currentActiveEncounterAssignToBed.getId().equals(existingEncounterAssignToBed.getId())) {
            throw new BadRequestAlertException(
                    "Only the current active assignment can be updated.",
                    "encounterAssignToBed",
                    "update.onlyActiveAllowed"
            );
        }

        validateBedAvailabilityForUpdate(updateDTO.bedId(), currentActiveEncounterAssignToBed.getBedId());
        bedHelper.validateBedExists(updateDTO.bedId());
        roomHelper.validateRoomExists(updateDTO.roomId());
        departmentHelper.validateDepartmentExists(updateDTO.departmentId());

        Instant transferTime = Instant.now();

        Long previousRoomId = currentActiveEncounterAssignToBed.getRoomId();
        Long previousBedId = currentActiveEncounterAssignToBed.getBedId();
        String previousAdmissionReason = currentActiveEncounterAssignToBed.getAdmissionReason();

        currentActiveEncounterAssignToBed.setIsActive(false);
        currentActiveEncounterAssignToBed.setReleasedAt(transferTime);

        EncounterAssignToBed newEncounterAssignToBedRecord = EncounterAssignToBed.builder()
                .encounter(patientEncounter)
                .patient(patient)
                .roomId(updateDTO.roomId())
                .bedId(updateDTO.bedId())
                .departmentId(updateDTO.departmentId())
                .admissionReason(previousAdmissionReason)
                .assignedAt(transferTime)
                .releasedAt(null)
                .isActive(true)
                .build();

        try {
            encounterAssignToBedRepository.saveAndFlush(currentActiveEncounterAssignToBed);

            EncounterAssignToBed createdUpdatedEncounterAssignToBed =
                    encounterAssignToBedRepository.saveAndFlush(newEncounterAssignToBedRecord);
            Long fromDepartmentId = currentActiveEncounterAssignToBed.getDepartmentId();
            Long toDepartmentId = updateDTO.departmentId();
            Boolean isExternal = !fromDepartmentId.equals(toDepartmentId);

            bedTransactionService.create(new BedTransactionCreateDTO(
                    updateDTO.encounterId(),
                    updateDTO.patientId(),
                    previousRoomId,
                    previousBedId,
                    updateDTO.roomId(),
                    updateDTO.bedId(),
                    fromDepartmentId,
                    toDepartmentId,
                    isExternal,
                    BedTransactionType.TRANSFER
            ));
            bedHelper.markAsInCleaning(previousBedId);

            LOG.info("[UPDATE] EncounterAssignToBed success oldId={} newId={} encounterId={} patientId={} roomId={} bedId={}",
                    currentActiveEncounterAssignToBed.getId(),
                    createdUpdatedEncounterAssignToBed.getId(),
                    updateDTO.encounterId(),
                    updateDTO.patientId(),
                    updateDTO.roomId(),
                    updateDTO.bedId());

            return createdUpdatedEncounterAssignToBed;
        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            LOG.warn("[UPDATE] EncounterAssignToBed failed (constraint) id={} payload={}",
                    encounterAssignToBedId, updateDTO, exception);
            throw handleConstraintViolation(exception);
        } catch (RuntimeException exception) {
            LOG.error("[UPDATE] EncounterAssignToBed failed (unexpected) id={} payload={}",
                    encounterAssignToBedId, updateDTO, exception);
            throw exception;
        }
    }

    public EncounterAssignToBed release(Long encounterAssignToBedId) {
        LOG.info("[RELEASE] EncounterAssignToBed id={}", encounterAssignToBedId);

        EncounterAssignToBed existingEncounterAssignToBed = encounterAssignToBedRepository.findById(encounterAssignToBedId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "EncounterAssignToBed not found with id " + encounterAssignToBedId,
                        "encounterAssignToBed",
                        "id.notfound"
                ));

        if (!Boolean.TRUE.equals(existingEncounterAssignToBed.getIsActive())) {
            throw new BadRequestAlertException(
                    "Assignment is already inactive.",
                    "encounterAssignToBed",
                    "assignment.alreadyInactive"
            );
        }

        Instant releaseTime = Instant.now();

        existingEncounterAssignToBed.setIsActive(false);
        existingEncounterAssignToBed.setReleasedAt(releaseTime);

        try {
            EncounterAssignToBed releasedEncounterAssignToBed =
                    encounterAssignToBedRepository.saveAndFlush(existingEncounterAssignToBed);

            bedTransactionService.create(new BedTransactionCreateDTO(
                    releasedEncounterAssignToBed.getEncounter().getId(),
                    releasedEncounterAssignToBed.getPatient().getId(),
                    releasedEncounterAssignToBed.getRoomId(),
                    releasedEncounterAssignToBed.getBedId(),
                    null,
                    null,
                    releasedEncounterAssignToBed.getDepartmentId(),
                    releasedEncounterAssignToBed.getDepartmentId(),
                    false,
                    BedTransactionType.RELEASE
            ));
            bedHelper.markAsInCleaning(releasedEncounterAssignToBed.getBedId());

            LOG.info("[RELEASE] EncounterAssignToBed success id={} encounterId={} bedId={}",
                    releasedEncounterAssignToBed.getId(),
                    releasedEncounterAssignToBed.getEncounter().getId(),
                    releasedEncounterAssignToBed.getBedId());

            return releasedEncounterAssignToBed;
        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            LOG.warn("[RELEASE] EncounterAssignToBed failed (constraint) id={}", encounterAssignToBedId, exception);
            throw handleConstraintViolation(exception);
        }
    }

    @Transactional(readOnly = true)
    public EncounterAssignToBed getById(Long encounterAssignToBedId) {
        LOG.debug("[GET_BY_ID] encounterAssignToBedId={}", encounterAssignToBedId);

        return encounterAssignToBedRepository.findById(encounterAssignToBedId)
                .orElseThrow(() -> {
                    LOG.warn("[GET_BY_ID] EncounterAssignToBed not found id={}", encounterAssignToBedId);
                    return new NotFoundAlertException(
                            "EncounterAssignToBed not found with id " + encounterAssignToBedId,
                            "encounterAssignToBed",
                            "id.notfound"
                    );
                });
    }

    @Transactional(readOnly = true)
    public EncounterAssignToBed getActiveAssignmentByEncounterId(Long encounterId) {
        LOG.debug("[GET_ACTIVE_BY_ENCOUNTER] encounterId={}", encounterId);

        return encounterAssignToBedRepository.findByEncounter_IdAndIsActiveTrue(encounterId)
                .orElseThrow(() -> {
                    LOG.warn("[GET_ACTIVE_BY_ENCOUNTER] Active assignment not found encounterId={}", encounterId);
                    return new NotFoundAlertException(
                            "Active assignment not found for encounter id " + encounterId,
                            "encounterAssignToBed",
                            "activeAssignment.notfound"
                    );
                });
    }

    @Transactional(readOnly = true)
    public List<Long> getActiveBedIds() {
        LOG.debug("[GET_ACTIVE_BED_IDS]");

        return encounterAssignToBedRepository.findAll()
                .stream()
                .filter(encounterAssignToBed -> Boolean.TRUE.equals(encounterAssignToBed.getIsActive()))
                .map(EncounterAssignToBed::getBedId)
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> getActiveRoomIds() {
        LOG.debug("[GET_ACTIVE_ROOM_IDS]");

        return encounterAssignToBedRepository.findAll()
                .stream()
                .filter(encounterAssignToBed -> Boolean.TRUE.equals(encounterAssignToBed.getIsActive()))
                .map(EncounterAssignToBed::getRoomId)
                .distinct()
                .toList();
    }

    private void validatePatientMatchesEncounter(PatientEncounter patientEncounter, Patient patient) {
        if (!patientEncounter.getPatient().getId().equals(patient.getId())) {
            throw new BadRequestAlertException(
                    "The provided patient does not belong to the provided encounter.",
                    "encounterAssignToBed",
                    "patient.encounter.mismatch"
            );
        }
    }

    private void validateEncounterHasNoActiveAssignment(Long encounterId) {
        boolean encounterAlreadyHasActiveAssignment =
                encounterAssignToBedRepository.findByEncounter_IdAndIsActiveTrue(encounterId).isPresent();

        if (encounterAlreadyHasActiveAssignment) {
            throw new BadRequestAlertException(
                    "Encounter already has an active bed assignment.",
                    "encounterAssignToBed",
                    "encounter.activeAssignment.exists"
            );
        }
    }

    private void validateBedAvailabilityForCreate(Long requestedBedId) {
        boolean requestedBedAlreadyAssigned =
                encounterAssignToBedRepository.existsByBedIdAndIsActiveTrue(requestedBedId);

        if (requestedBedAlreadyAssigned) {
            throw new BadRequestAlertException(
                    "Bed is already assigned to another active encounter.",
                    "encounterAssignToBed",
                    "bed.alreadyAssigned"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<EncounterAssignToBed> getActiveAssignmentsByEncounterIds(List<Long> encounterIds) {
        LOG.debug("[GET_ACTIVE_LIST_BY_ENCOUNTERS] encounterIds={}", encounterIds);

        List<EncounterAssignToBed> activeAssignments =
                encounterAssignToBedRepository.findAllByEncounter_IdInAndIsActiveTrue(encounterIds);

        if (activeAssignments.isEmpty()) {
            LOG.warn("[GET_ACTIVE_LIST_BY_ENCOUNTERS] No active assignments found for encounterIds={}", encounterIds);
            throw new NotFoundAlertException(
                    "No active assignments found for encounter ids " + encounterIds,
                    "encounterAssignToBed",
                    "activeAssignments.notfound"
            );
        }

        return activeAssignments;
    }

    public void dischargeActiveAssignmentByEncounterId(Long encounterId) {
        LOG.info("[DISCHARGE_ASSIGNMENT] encounterId={}", encounterId);

        Optional<EncounterAssignToBed> optionalActiveAssignment =
                encounterAssignToBedRepository.findByEncounter_IdAndIsActiveTrue(encounterId);

        if (optionalActiveAssignment.isEmpty()) {
            LOG.info("[DISCHARGE_ASSIGNMENT] No active bed assignment found for encounterId={}, skipping discharge bed release.",
                    encounterId);
            return;
        }

        EncounterAssignToBed activeAssignment = optionalActiveAssignment.get();

        if (!Boolean.TRUE.equals(activeAssignment.getIsActive())) {
            LOG.info("[DISCHARGE_ASSIGNMENT] Assignment already inactive id={} encounterId={}, skipping.",
                    activeAssignment.getId(), encounterId);
            return;
        }

        Instant dischargeReleaseTime = Instant.now();

        activeAssignment.setIsActive(false);
        activeAssignment.setReleasedAt(dischargeReleaseTime);

        try {
            EncounterAssignToBed dischargedAssignment =
                    encounterAssignToBedRepository.saveAndFlush(activeAssignment);

            bedTransactionService.create(new BedTransactionCreateDTO(
                    dischargedAssignment.getEncounter().getId(),
                    dischargedAssignment.getPatient().getId(),
                    dischargedAssignment.getRoomId(),
                    dischargedAssignment.getBedId(),
                    null,
                    null,
                    dischargedAssignment.getDepartmentId(),
                    dischargedAssignment.getDepartmentId(),
                    false,
                    BedTransactionType.DISCHARGE
            ));
            bedHelper.markAsInCleaning(dischargedAssignment.getBedId());

            LOG.info("[DISCHARGE_ASSIGNMENT] success assignmentId={} encounterId={} roomId={} bedId={}",
                    dischargedAssignment.getId(),
                    dischargedAssignment.getEncounter().getId(),
                    dischargedAssignment.getRoomId(),
                    dischargedAssignment.getBedId());

        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            LOG.warn("[DISCHARGE_ASSIGNMENT] failed (constraint) encounterId={}", encounterId, exception);
            throw handleConstraintViolation(exception);
        } catch (RuntimeException exception) {
            LOG.error("[DISCHARGE_ASSIGNMENT] failed (unexpected) encounterId={}", encounterId, exception);
            throw exception;
        }
    }

    private void validateBedAvailabilityForUpdate(Long requestedBedId, Long currentActiveBedId) {
        if (requestedBedId.equals(currentActiveBedId)) {
            throw new BadRequestAlertException(
                    "Transfer to the same bed is not allowed.",
                    "encounterAssignToBed",
                    "transfer.sameBed.notAllowed"
            );
        }

        boolean requestedBedAlreadyAssigned =
                encounterAssignToBedRepository.existsByBedIdAndIsActiveTrue(requestedBedId);

        if (requestedBedAlreadyAssigned) {
            throw new BadRequestAlertException(
                    "Bed is already assigned to another active encounter.",
                    "encounterAssignToBed",
                    "bed.alreadyAssigned"
            );
        }
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable rootCause = getRootCause(exception);
        String rootMessage = rootCause != null ? rootCause.getMessage() : exception.getMessage();
        String rootMessageLower = rootMessage != null ? rootMessage.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] EncounterAssignToBed constraint violated rootMessage={}", rootMessage, exception);

        if (rootMessageLower.contains("uk_encounter_assign_bed_active_encounter")) {
            return new BadRequestAlertException(
                    "Encounter already has an active bed assignment.",
                    "encounterAssignToBed",
                    "encounter.activeAssignment.exists"
            );
        }

        if (rootMessageLower.contains("uk_encounter_assign_bed_active_bed")) {
            return new BadRequestAlertException(
                    "Bed is already assigned to another active encounter.",
                    "encounterAssignToBed",
                    "bed.alreadyAssigned"
            );
        }

        if (rootMessageLower.contains("fk_encounter_assign_bed_encounter")) {
            return new NotFoundAlertException(
                    "Encounter not found.",
                    "encounterAssignToBed",
                    "encounter.notfound"
            );
        }

        if (rootMessageLower.contains("fk_encounter_assign_bed_patient")) {
            return new NotFoundAlertException(
                    "Patient not found.",
                    "encounterAssignToBed",
                    "patient.notfound"
            );
        }

        if (rootMessageLower.contains("fk_encounter_assign_bed_room")) {
            return new NotFoundAlertException(
                    "Room not found.",
                    "encounterAssignToBed",
                    "room.notfound"
            );
        }

        if (rootMessageLower.contains("fk_encounter_assign_bed_bed")) {
            return new NotFoundAlertException(
                    "Bed not found.",
                    "encounterAssignToBed",
                    "bed.notfound"
            );
        }

        if (rootMessageLower.contains("fk_encounter_assign_bed_department")) {
            return new NotFoundAlertException(
                    "Department not found.",
                    "encounterAssignToBed",
                    "department.notfound"
            );
        }

        if (rootMessageLower.contains("ck_encounter_assign_bed_release_logic")) {
            return new BadRequestAlertException(
                    "Invalid release state. Active assignment must not have releasedAt.",
                    "encounterAssignToBed",
                    "release.logic.invalid"
            );
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving encounter assign to bed.",
                "encounterAssignToBed",
                "db.constraint"
        );
    }

}