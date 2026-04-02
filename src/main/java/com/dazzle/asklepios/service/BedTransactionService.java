package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BedTransaction;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.BedTransactionType;
import com.dazzle.asklepios.repository.BedTransactionRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.bedTransaction.BedTransactionCreateDTO;
import com.dazzle.asklepios.service.dto.bedTransaction.BedTransactionUpdateDTO;
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

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class BedTransactionService {

    private static final Logger LOG = LoggerFactory.getLogger(BedTransactionService.class);

    private final BedTransactionRepository bedTransactionRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientRepository patientRepository;

    public BedTransaction create(BedTransactionCreateDTO createDTO) {
        LOG.info("[CREATE] BedTransaction payload={}", createDTO);

        PatientEncounter patientEncounter = patientEncounterRepository.findById(createDTO.encounterId())
                .orElseThrow(() -> {
                    LOG.warn("[CREATE] BedTransaction rejected: encounter not found encounterId={}", createDTO.encounterId());
                    return new NotFoundAlertException(
                            "Encounter not found with id " + createDTO.encounterId(),
                            "bedTransaction",
                            "encounter.notfound"
                    );
                });

        Patient patient = patientRepository.findById(createDTO.patientId())
                .orElseThrow(() -> {
                    LOG.warn("[CREATE] BedTransaction rejected: patient not found patientId={}", createDTO.patientId());
                    return new NotFoundAlertException(
                            "Patient not found with id " + createDTO.patientId(),
                            "bedTransaction",
                            "patient.notfound"
                    );
                });

        validatePatientMatchesEncounter(patientEncounter, patient);
        validateTransactionDirection(
                createDTO.transactionType(),
                createDTO.fromRoomId(),
                createDTO.fromBedId(),
                createDTO.toRoomId(),
                createDTO.toBedId()
        );
        validateTransferNotSameBed(
                createDTO.transactionType(),
                createDTO.fromBedId(),
                createDTO.toBedId()
        );
        validateDepartmentTransition(
                createDTO.fromDepartmentId(),
                createDTO.toDepartmentId(),
                createDTO.isExternal()
        );

        BedTransaction bedTransactionToCreate = BedTransaction.builder()
                .encounter(patientEncounter)
                .patient(patient)
                .fromRoomId(createDTO.fromRoomId())
                .fromBedId(createDTO.fromBedId())
                .toRoomId(createDTO.toRoomId())
                .toBedId(createDTO.toBedId())
                .fromDepartmentId(createDTO.fromDepartmentId())
                .toDepartmentId(createDTO.toDepartmentId())
                .isExternal(createDTO.isExternal())
                .transactionType(createDTO.transactionType())
                .transactionDate(Instant.now())
                .build();

        try {
            BedTransaction createdBedTransaction = bedTransactionRepository.save(bedTransactionToCreate);

            LOG.info("[CREATE] BedTransaction success id={} encounterId={} patientId={} transactionType={}",
                    createdBedTransaction.getId(),
                    createDTO.encounterId(),
                    createDTO.patientId(),
                    createDTO.transactionType());

            return createdBedTransaction;
        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            LOG.warn("[CREATE] BedTransaction failed (constraint) payload={}", createDTO, exception);
            throw handleConstraintViolation(exception);
        } catch (RuntimeException exception) {
            LOG.error("[CREATE] BedTransaction failed (unexpected) payload={}", createDTO, exception);
            throw exception;
        }
    }

    public BedTransaction update(Long bedTransactionId, BedTransactionUpdateDTO updateDTO) {
        LOG.info("[UPDATE] BedTransaction id={} payload={}", bedTransactionId, updateDTO);

        if (!bedTransactionId.equals(updateDTO.id())) {
            throw new BadRequestAlertException(
                    "Path id does not match payload id.",
                    "bedTransaction",
                    "id.mismatch"
            );
        }

        BedTransaction existingBedTransaction = bedTransactionRepository.findById(bedTransactionId)
                .orElseThrow(() -> {
                    LOG.warn("[UPDATE] BedTransaction rejected: not found id={}", bedTransactionId);
                    return new NotFoundAlertException(
                            "BedTransaction not found with id " + bedTransactionId,
                            "bedTransaction",
                            "id.notfound"
                    );
                });

        PatientEncounter patientEncounter = patientEncounterRepository.findById(updateDTO.encounterId())
                .orElseThrow(() -> {
                    LOG.warn("[UPDATE] BedTransaction rejected: encounter not found encounterId={}", updateDTO.encounterId());
                    return new NotFoundAlertException(
                            "Encounter not found with id " + updateDTO.encounterId(),
                            "bedTransaction",
                            "encounter.notfound"
                    );
                });

        Patient patient = patientRepository.findById(updateDTO.patientId())
                .orElseThrow(() -> {
                    LOG.warn("[UPDATE] BedTransaction rejected: patient not found patientId={}", updateDTO.patientId());
                    return new NotFoundAlertException(
                            "Patient not found with id " + updateDTO.patientId(),
                            "bedTransaction",
                            "patient.notfound"
                    );
                });

        validatePatientMatchesEncounter(patientEncounter, patient);
        validateTransactionDirection(
                updateDTO.transactionType(),
                updateDTO.fromRoomId(),
                updateDTO.fromBedId(),
                updateDTO.toRoomId(),
                updateDTO.toBedId()
        );
        validateTransferNotSameBed(
                updateDTO.transactionType(),
                updateDTO.fromBedId(),
                updateDTO.toBedId()
        );
        validateDepartmentTransition(
                updateDTO.fromDepartmentId(),
                updateDTO.toDepartmentId(),
                updateDTO.isExternal()
        );

        existingBedTransaction.setEncounter(patientEncounter);
        existingBedTransaction.setPatient(patient);
        existingBedTransaction.setFromRoomId(updateDTO.fromRoomId());
        existingBedTransaction.setFromBedId(updateDTO.fromBedId());
        existingBedTransaction.setToRoomId(updateDTO.toRoomId());
        existingBedTransaction.setToBedId(updateDTO.toBedId());
        existingBedTransaction.setFromDepartmentId(updateDTO.fromDepartmentId());
        existingBedTransaction.setToDepartmentId(updateDTO.toDepartmentId());
        existingBedTransaction.setIsExternal(updateDTO.isExternal());
        existingBedTransaction.setTransactionType(updateDTO.transactionType());

        try {
            BedTransaction updatedBedTransaction = bedTransactionRepository.save(existingBedTransaction);

            LOG.info("[UPDATE] BedTransaction success id={} encounterId={} patientId={} transactionType={}",
                    updatedBedTransaction.getId(),
                    updateDTO.encounterId(),
                    updateDTO.patientId(),
                    updateDTO.transactionType());

            return updatedBedTransaction;
        } catch (DataIntegrityViolationException | JpaSystemException exception) {
            LOG.warn("[UPDATE] BedTransaction failed (constraint) id={} payload={}", bedTransactionId, updateDTO, exception);
            throw handleConstraintViolation(exception);
        } catch (RuntimeException exception) {
            LOG.error("[UPDATE] BedTransaction failed (unexpected) id={} payload={}", bedTransactionId, updateDTO, exception);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public BedTransaction getById(Long bedTransactionId) {
        LOG.debug("[GET_BY_ID] bedTransactionId={}", bedTransactionId);

        return bedTransactionRepository.findById(bedTransactionId)
                .orElseThrow(() -> {
                    LOG.warn("[GET_BY_ID] BedTransaction not found id={}", bedTransactionId);
                    return new NotFoundAlertException(
                            "BedTransaction not found with id " + bedTransactionId,
                            "bedTransaction",
                            "id.notfound"
                    );
                });
    }
    @Transactional(readOnly = true)
    public Page<BedTransaction> getByToDepartmentAndTransactionDateRange(
            Long departmentId,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        LOG.debug("[GET_BY_TO_DEPARTMENT_AND_DATE_RANGE] departmentId={} from={} to={} pageable={}",
                departmentId, from, to, pageable);

        return bedTransactionRepository.findByToDepartmentIdAndTransactionDateBetween(
                departmentId,
                from,
                to,
                pageable
        );
    }

    private void validatePatientMatchesEncounter(PatientEncounter patientEncounter, Patient patient) {
        if (!patientEncounter.getPatient().getId().equals(patient.getId())) {
            throw new BadRequestAlertException(
                    "The provided patient does not belong to the provided encounter.",
                    "bedTransaction",
                    "patient.encounter.mismatch"
            );
        }
    }

    private void validateTransactionDirection(
            BedTransactionType transactionType,
            Long fromRoomId,
            Long fromBedId,
            Long toRoomId,
            Long toBedId
    ) {
        if (transactionType == BedTransactionType.ASSIGN) {
            boolean invalidAssign =
                    fromRoomId != null
                            || fromBedId != null
                            || toRoomId == null
                            || toBedId == null;

            if (invalidAssign) {
                throw new BadRequestAlertException(
                        "ASSIGN must have null source room and bed, and non-null destination room and bed.",
                        "bedTransaction",
                        "assign.direction.invalid"
                );
            }
        }

        if (transactionType == BedTransactionType.TRANSFER) {
            boolean invalidTransfer =
                    fromRoomId == null
                            || fromBedId == null
                            || toRoomId == null
                            || toBedId == null;

            if (invalidTransfer) {
                throw new BadRequestAlertException(
                        "TRANSFER must have non-null source room and bed, and non-null destination room and bed.",
                        "bedTransaction",
                        "transfer.direction.invalid"
                );
            }
        }

        if (transactionType == BedTransactionType.RELEASE || transactionType == BedTransactionType.DISCHARGE) {
            boolean invalidReleaseOrDischarge =
                    fromRoomId == null
                            || fromBedId == null
                            || toRoomId != null
                            || toBedId != null;

            if (invalidReleaseOrDischarge) {
                throw new BadRequestAlertException(
                        "RELEASE and DISCHARGE must have non-null source room and bed, and null destination room and bed.",
                        "bedTransaction",
                        "releaseOrDischarge.direction.invalid"
                );
            }
        }
    }

    private void validateTransferNotSameBed(
            BedTransactionType transactionType,
            Long fromBedId,
            Long toBedId
    ) {
        if (transactionType != BedTransactionType.TRANSFER) {
            return;
        }

        if (fromBedId != null && toBedId != null && fromBedId.equals(toBedId)) {
            throw new BadRequestAlertException(
                    "Transfer to the same bed is not allowed.",
                    "bedTransaction",
                    "transfer.sameBed.notAllowed"
            );
        }
    }

    private void validateDepartmentTransition(
            Long fromDepartmentId,
            Long toDepartmentId,
            Boolean isExternal
    ) {
        if (fromDepartmentId == null || toDepartmentId == null || isExternal == null) {
            throw new BadRequestAlertException(
                    "Department transition fields are required.",
                    "bedTransaction",
                    "departmentTransition.required"
            );
        }

        if (!isExternal && !fromDepartmentId.equals(toDepartmentId)) {
            throw new BadRequestAlertException(
                    "Internal transaction must have the same source and destination department.",
                    "bedTransaction",
                    "departmentTransition.internal.invalid"
            );
        }

        if (isExternal && fromDepartmentId.equals(toDepartmentId)) {
            throw new BadRequestAlertException(
                    "External transaction must have different source and destination departments.",
                    "bedTransaction",
                    "departmentTransition.external.invalid"
            );
        }
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable rootCause = getRootCause(exception);
        String rootMessage = rootCause != null ? rootCause.getMessage() : exception.getMessage();
        String rootMessageLower = rootMessage != null ? rootMessage.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] BedTransaction constraint violated rootMessage={}", rootMessage, exception);

        if (rootMessageLower.contains("fk_bed_transaction_encounter")) {
            return new NotFoundAlertException(
                    "Encounter not found.",
                    "bedTransaction",
                    "encounter.notfound"
            );
        }

        if (rootMessageLower.contains("fk_bed_transaction_patient")) {
            return new NotFoundAlertException(
                    "Patient not found.",
                    "bedTransaction",
                    "patient.notfound"
            );
        }

        if (rootMessageLower.contains("fk_bed_transaction_from_room")) {
            return new NotFoundAlertException(
                    "Source room not found.",
                    "bedTransaction",
                    "fromRoom.notfound"
            );
        }

        if (rootMessageLower.contains("fk_bed_transaction_from_bed")) {
            return new NotFoundAlertException(
                    "Source bed not found.",
                    "bedTransaction",
                    "fromBed.notfound"
            );
        }

        if (rootMessageLower.contains("fk_bed_transaction_to_room")) {
            return new NotFoundAlertException(
                    "Destination room not found.",
                    "bedTransaction",
                    "toRoom.notfound"
            );
        }

        if (rootMessageLower.contains("fk_bed_transaction_to_bed")) {
            return new NotFoundAlertException(
                    "Destination bed not found.",
                    "bedTransaction",
                    "toBed.notfound"
            );
        }

        if (rootMessageLower.contains("fk_bed_transaction_from_department")) {
            return new NotFoundAlertException(
                    "Source department not found.",
                    "bedTransaction",
                    "fromDepartment.notfound"
            );
        }

        if (rootMessageLower.contains("fk_bed_transaction_to_department")) {
            return new NotFoundAlertException(
                    "Destination department not found.",
                    "bedTransaction",
                    "toDepartment.notfound"
            );
        }

        if (rootMessageLower.contains("ck_bed_transaction_type")) {
            return new BadRequestAlertException(
                    "Invalid bed transaction type.",
                    "bedTransaction",
                    "transactionType.invalid"
            );
        }

        if (rootMessageLower.contains("ck_bed_transaction_external_flag")) {
            return new BadRequestAlertException(
                    "Invalid external flag and department transition combination.",
                    "bedTransaction",
                    "externalFlag.invalid"
            );
        }

        if (rootMessageLower.contains("ck_bed_transaction_direction")) {
            return new BadRequestAlertException(
                    "Invalid bed transaction direction fields.",
                    "bedTransaction",
                    "transactionDirection.invalid"
            );
        }

        if (rootMessageLower.contains("ck_bed_transaction_not_same_bed")) {
            return new BadRequestAlertException(
                    "Transfer to the same bed is not allowed.",
                    "bedTransaction",
                    "transfer.sameBed.notAllowed"
            );
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving bed transaction.",
                "bedTransaction",
                "db.constraint"
        );
    }
}