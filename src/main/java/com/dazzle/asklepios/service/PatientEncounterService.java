package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterCreateDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterSearchFilterDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientEncounterService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientEncounterService.class);

    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientRepository patientRepository;

    public PatientEncounter create(PatientEncounterCreateDTO createDTO) {
        LOG.info("[CREATE] PatientEncounter payload={}", createDTO);

        Patient patient = patientRepository.findById(createDTO.patientId())
                .orElseThrow(() -> {
                    LOG.warn("[CREATE] PatientEncounter rejected: patient not found patientId={}", createDTO.patientId());
                    return new NotFoundAlertException(
                            "Patient not found with id " + createDTO.patientId(),
                            "patientEncounter",
                            "patient.notfound"
                    );
                });

        PatientEncounter patientEncounterToCreate = PatientEncounter.builder()
                .patient(patient)
                .facilityId(createDTO.facilityId())
                .departmentId(createDTO.departmentId())
                .practitionerId(createDTO.practitionerId())
                .encounterType(createDTO.encounterType())
                .encounterReason(createDTO.encounterReason())
                .followUpEncounter(createDTO.followUpEncounterId() == null ? null :
                        patientEncounterRepository.findById(createDTO.followUpEncounterId())
                                .orElseThrow(() -> new NotFoundAlertException(
                                        "Follow-up encounter not found with id " + createDTO.followUpEncounterId(),
                                        "patientEncounter",
                                        "followUpEncounter.notfound"
                                ))
                )
                .priorityLevel(createDTO.priorityLevel())
                .originType(createDTO.originType())
                .originName(createDTO.originName())
                .notes(createDTO.notes())
                .chiefComplaint(createDTO.chiefComplaint())
                .hasOrder(createDTO.hasOrder())
                .isObserved(createDTO.isObserved())
                .hasPrescription(createDTO.hasPrescription())
                .status(EncounterStatus.PENDING_PAYMENT)
                .build();

        try {
            PatientEncounter createdPatientEncounter = patientEncounterRepository.saveAndFlush(patientEncounterToCreate);
            LOG.info("[CREATE] PatientEncounter success id={} patientId={} departmentId={} status={}",
                    createdPatientEncounter.getId(),
                    createDTO.patientId(),
                    createDTO.departmentId(),
                    createdPatientEncounter.getStatus()
            );
            return createdPatientEncounter;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CREATE] PatientEncounter failed (constraint) payload={}", createDTO, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[CREATE] PatientEncounter failed (unexpected) payload={}", createDTO, ex);
            throw ex;
        }
    }

    public PatientEncounter update(Long patientEncounterId, PatientEncounterUpdateDTO updateDTO) {
        LOG.info("[UPDATE] PatientEncounter id={} payload={}", patientEncounterId, updateDTO);

        PatientEncounter existingPatientEncounter = patientEncounterRepository.findById(patientEncounterId)
                .orElseThrow(() -> {
                    LOG.warn("[UPDATE] PatientEncounter rejected: not found id={}", patientEncounterId);
                    return new NotFoundAlertException(
                            "PatientEncounter not found with id " + patientEncounterId,
                            "patientEncounter",
                            "id.notfound"
                    );
                });

        Patient patient = patientRepository.findById(updateDTO.patientId())
                .orElseThrow(() -> {
                    LOG.warn("[UPDATE] PatientEncounter rejected: patient not found patientId={} encounterId={}",
                            updateDTO.patientId(), patientEncounterId);
                    return new NotFoundAlertException(
                            "Patient not found with id " + updateDTO.patientId(),
                            "patientEncounter",
                            "patient.notfound"
                    );
                });

        existingPatientEncounter.setPatient(patient);
        existingPatientEncounter.setFacilityId(updateDTO.facilityId());
        existingPatientEncounter.setDepartmentId(updateDTO.departmentId());
        existingPatientEncounter.setPractitionerId(updateDTO.practitionerId());
        existingPatientEncounter.setEncounterType(updateDTO.encounterType());
        existingPatientEncounter.setEncounterReason(updateDTO.encounterReason());
        existingPatientEncounter.setPriorityLevel(updateDTO.priorityLevel());
        existingPatientEncounter.setOriginType(updateDTO.originType());
        existingPatientEncounter.setOriginName(updateDTO.originName());
        existingPatientEncounter.setNotes(updateDTO.notes());
        existingPatientEncounter.setStatus(updateDTO.status());
        existingPatientEncounter.setChiefComplaint(updateDTO.chiefComplaint());
        existingPatientEncounter.setHasOrder(updateDTO.hasOrder());
        existingPatientEncounter.setIsObserved(updateDTO.isObserved());
        existingPatientEncounter.setHasPrescription(updateDTO.hasPrescription());

        if (updateDTO.followUpEncounterId() != null) {
            PatientEncounter followUpEncounter = patientEncounterRepository.findById(updateDTO.followUpEncounterId())
                    .orElseThrow(() -> new NotFoundAlertException(
                            "Follow-up encounter not found with id " + updateDTO.followUpEncounterId(),
                            "patientEncounter",
                            "followUpEncounter.notfound"
                    ));
            existingPatientEncounter.setFollowUpEncounter(followUpEncounter);
        } else {
            existingPatientEncounter.setFollowUpEncounter(null);
        }

        try {
            PatientEncounter updatedPatientEncounter = patientEncounterRepository.saveAndFlush(existingPatientEncounter);
            LOG.info("[UPDATE] PatientEncounter success id={} patientId={} departmentId={} status={}",
                    updatedPatientEncounter.getId(),
                    updateDTO.patientId(),
                    updateDTO.departmentId(),
                    updateDTO.status()
            );
            return updatedPatientEncounter;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[UPDATE] PatientEncounter failed (constraint) id={} payload={}", patientEncounterId, updateDTO, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[UPDATE] PatientEncounter failed (unexpected) id={} payload={}", patientEncounterId, updateDTO, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<PatientEncounter> filterEncounters(PatientEncounterSearchFilterDTO filter, Pageable pageable) {
        LOG.debug("Service filter PatientEncounters filter={} pageable={}", filter, pageable);

        LocalDate today = LocalDate.now();

        LocalDate effectiveFrom = (filter.fromDate() != null) ? filter.fromDate() : today;
        LocalDate effectiveTo = (filter.toDate() != null) ? filter.toDate() : today;

        List<EncounterStatus> effectiveStatuses =
                (filter.statuses() != null && !filter.statuses().isEmpty())
                        ? filter.statuses()
                        : List.of(EncounterStatus.NEW, EncounterStatus.ONGOING);

        boolean hasPatientName = filter.patientName() != null && !filter.patientName().isBlank();
        boolean hasMrn = filter.mrn() != null && !filter.mrn().isBlank();
        boolean hasChief = filter.chiefComplaint() != null && !filter.chiefComplaint().isBlank();

        LOG.debug("[FILTER] effectiveFrom={} effectiveTo={} statuses={} hasPatientName={} hasMrn={} hasChief={} hasPrescription={} hasOrder={} isObserved={}",
                effectiveFrom, effectiveTo, effectiveStatuses, hasPatientName, hasMrn, hasChief,
                filter.hasPrescription(), filter.hasOrder(), filter.isObserved());

        Specification<PatientEncounter> encounterFilterSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("departmentId"), filter.departmentId()));
            predicates.add(cb.between(root.get("encounterDate"), effectiveFrom, effectiveTo));
            predicates.add(root.get("status").in(effectiveStatuses));

            if (filter.encounterReasons() != null && !filter.encounterReasons().isEmpty()) {
                predicates.add(root.get("encounterReason").in(filter.encounterReasons()));
            }

            if (filter.priorities() != null && !filter.priorities().isEmpty()) {
                predicates.add(root.get("priorityLevel").in(filter.priorities()));
            }

            if (hasChief) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("chiefComplaint")),
                                "%" + filter.chiefComplaint().trim().toLowerCase() + "%"
                        )
                );
            }

            if (filter.hasPrescription() != null) {
                predicates.add(cb.equal(root.get("hasPrescription"), filter.hasPrescription()));
            }

            if (filter.hasOrder() != null) {
                predicates.add(cb.equal(root.get("hasOrder"), filter.hasOrder()));
            }

            if (filter.isObserved() != null) {
                predicates.add(cb.equal(root.get("isObserved"), filter.isObserved()));
            }

            if (hasPatientName || hasMrn) {
                Join<PatientEncounter, Patient> patientJoin = root.join("patient", JoinType.INNER);

                if (hasMrn) {
                    predicates.add(cb.equal(patientJoin.get("medicalRecordNumber"), filter.mrn().trim()));
                }

                if (hasPatientName) {
                    String raw = filter.patientName();
                    if (raw != null) {
                        String[] tokens = raw.trim().toLowerCase().split("\\s+");

                        Expression<String> first = cb.lower(cb.coalesce(patientJoin.get("firstName"), ""));
                        Expression<String> second = cb.lower(cb.coalesce(patientJoin.get("secondName"), ""));
                        Expression<String> third = cb.lower(cb.coalesce(patientJoin.get("thirdName"), ""));
                        Expression<String> last = cb.lower(cb.coalesce(patientJoin.get("lastName"), ""));

                        Predicate[] tokenPreds = java.util.Arrays.stream(tokens)
                                .filter(t -> t != null && !t.isBlank())
                                .map(t -> {
                                    String like = "%" + t + "%";
                                    return cb.or(
                                            cb.like(first, like),
                                            cb.like(second, like),
                                            cb.like(third, like),
                                            cb.like(last, like)
                                    );
                                })
                                .toArray(Predicate[]::new);

                        if (tokenPreds.length > 0) {
                            predicates.add(cb.and(tokenPreds));
                        }
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<PatientEncounter> result = patientEncounterRepository.findAll(encounterFilterSpec, pageable);

        LOG.debug("[FILTER] PatientEncounters result totalElements={} totalPages={} pageNumber={} pageSize={}",
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());

        return result;
    }

    @Transactional(readOnly = true)
    public Page<PatientEncounter> findPreviousByPatientAndDepartment(
            Long patientId,
            Long departmentId,
            Pageable pageable
    ) {
        List<EncounterStatus> completedEncounterStatuses = List.of(
                EncounterStatus.CLOSED,
                EncounterStatus.DISCHARGED
        );

        LOG.debug("[FIND_PREVIOUS_PAGE] patientId={} departmentId={} statuses={} pageable={}",
                patientId, departmentId, completedEncounterStatuses, pageable);

        return patientEncounterRepository.findByPatientIdAndDepartmentIdAndStatusInOrderByCreatedDateDesc(
                patientId,
                departmentId,
                completedEncounterStatuses,
                pageable
        );
    }

    public PatientEncounter startEncounter(Long encounterId) {
        LOG.info("[START] PatientEncounter id={}", encounterId);

        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + encounterId,
                        "patientEncounter",
                        "id.notfound"
                ));

        boolean hasOtherOngoing = patientEncounterRepository
                .existsByPatient_IdAndStatusAndIdNot(
                        encounter.getPatient().getId(),
                        EncounterStatus.ONGOING,
                        encounter.getId()
                );

        if (hasOtherOngoing) {
            throw new BadRequestAlertException(
                    "Patient already has an ONGOING encounter. Starting another one is not allowed.",
                    "patientEncounter",
                    "patient.hasOngoing.notAllowed"
            );
        }

        encounter.setStatus(EncounterStatus.ONGOING);

        try {
            PatientEncounter saved = patientEncounterRepository.saveAndFlush(encounter);
            LOG.info("[START] success id={} status={}", saved.getId(), saved.getStatus());
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[START] failed (constraint) id={}", encounterId, ex);
            throw handleConstraintViolation(ex);
        }
    }

    public PatientEncounter cancelEncounter(Long encounterId) {
        LOG.info("[CANCEL] PatientEncounter id={}", encounterId);

        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + encounterId,
                        "patientEncounter",
                        "id.notfound"
                ));

        // Cancel allowed when status is NEW OR isObserved is false
        if (encounter.getStatus() != EncounterStatus.NEW && Boolean.TRUE.equals(encounter.getIsObserved())) {
            throw new BadRequestAlertException(
                    "Cancel is allowed only when status is NEW OR isObserved is false.",
                    "patientEncounter",
                    "cancel.notAllowed.rule"
            );
        }

        // Use CANCELLED (double-L) to match DB + constraint
        encounter.setStatus(EncounterStatus.CANCELLED);

        try {
            PatientEncounter saved = patientEncounterRepository.saveAndFlush(encounter);
            LOG.info("[CANCEL] success id={} status={}", saved.getId(), saved.getStatus());
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CANCEL] failed (constraint) id={}", encounterId, ex);
            throw handleConstraintViolation(ex);
        }
    }

    public PatientEncounter dischargeEncounter(Long encounterId) {
        LOG.info("[DISCHARGE] PatientEncounter id={}", encounterId);

        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + encounterId,
                        "patientEncounter",
                        "id.notfound"
                ));

        if (encounter.getStatus() != EncounterStatus.ONGOING) {
            throw new BadRequestAlertException(
                    "Discharge allowed only when status is ONGOING.",
                    "patientEncounter",
                    "discharge.notAllowed"
            );
        }

        encounter.setStatus(EncounterStatus.DISCHARGED);

        PatientEncounter saved = patientEncounterRepository.saveAndFlush(encounter);
        LOG.info("[DISCHARGE] success id={} status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    public PatientEncounter completeEncounter(Long encounterId) {
        LOG.info("[COMPLETE] PatientEncounter id={}", encounterId);

        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + encounterId,
                        "patientEncounter",
                        "id.notfound"
                ));

        if (encounter.getStatus() != EncounterStatus.ONGOING) {
            throw new BadRequestAlertException(
                    "Complete allowed only when status is ONGOING.",
                    "patientEncounter",
                    "complete.notAllowed"
            );
        }

        encounter.setStatus(EncounterStatus.CLOSED);

        PatientEncounter saved = patientEncounterRepository.saveAndFlush(encounter);
        LOG.info("[COMPLETE] success id={} status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    @Transactional(readOnly = true)
    public long countTodayEncountersByFacility(Long facilityId) {
        LocalDate todayDate = LocalDate.now();

        LOG.debug("[COUNT_TODAY_FACILITY_ENCOUNTERS] facilityId={} todayDate={}",
                facilityId, todayDate);

        long totalEncounters =
                patientEncounterRepository.countByFacilityIdAndEncounterDate(facilityId, todayDate);

        LOG.debug("[COUNT_TODAY_FACILITY_ENCOUNTERS] facilityId={} todayDate={} total={}",
                facilityId, todayDate, totalEncounters);

        return totalEncounters;
    }

    @Transactional(readOnly = true)
    public long countTodayDepartmentTotalPatients(Long departmentId) {
        LocalDate today = LocalDate.now();

        LOG.debug("[DASHBOARD] COUNT_TOTAL_PATIENTS departmentId={} date={}", departmentId, today);

        long total = patientEncounterRepository
                .countDistinctPatient_IdByDepartmentIdAndEncounterDate(departmentId, today);

        LOG.debug("[DASHBOARD] COUNT_TOTAL_PATIENTS_RESULT departmentId={} date={} total={}",
                departmentId, today, total);

        return total;
    }

    @Transactional(readOnly = true)
    public long countTodayDepartmentActiveCases(Long departmentId) {
        LocalDate today = LocalDate.now();

        LOG.debug("[DASHBOARD] COUNT_ACTIVE_CASES departmentId={} date={}", departmentId, today);

        long active = patientEncounterRepository.countByDepartmentIdAndEncounterDateAndStatusIn(
                departmentId,
                today,
                List.of(EncounterStatus.NEW, EncounterStatus.ONGOING)
        );

        LOG.debug("[DASHBOARD] COUNT_ACTIVE_CASES_RESULT departmentId={} date={} total={}",
                departmentId, today, active);

        return active;
    }

    @Transactional(readOnly = true)
    public long countTodayDepartmentCompleted(Long departmentId) {
        LocalDate today = LocalDate.now();

        LOG.debug("[DASHBOARD] COUNT_COMPLETED departmentId={} date={}", departmentId, today);

        long completed = patientEncounterRepository
                .countByDepartmentIdAndEncounterDateAndStatus(
                        departmentId,
                        today,
                        EncounterStatus.CLOSED
                );

        LOG.debug("[DASHBOARD] COUNT_COMPLETED_RESULT departmentId={} date={} total={}",
                departmentId, today, completed);

        return completed;
    }

    @Transactional(readOnly = true)
    public long countTodayDepartmentCancelled(Long departmentId) {
        LocalDate today = LocalDate.now();

        LOG.debug("[DASHBOARD] COUNT_CANCELLED departmentId={} date={}", departmentId, today);

        long cancelled = patientEncounterRepository
                .countByDepartmentIdAndEncounterDateAndStatus(
                        departmentId,
                        today,
                        EncounterStatus.CANCELLED
                );

        LOG.debug("[DASHBOARD] COUNT_CANCELLED_RESULT departmentId={} date={} total={}",
                departmentId, today, cancelled);

        return cancelled;
    }

    @Transactional(readOnly = true)
    public Page<PatientEncounter> getEncountersByPatientId(
            Long patientId,
            Pageable pageable
    ) {
        LOG.debug("[GET_BY_PATIENT] patientId={} pageable={}", patientId, pageable);

        return patientEncounterRepository
                .findByPatientIdOrderByCreatedDateDesc(patientId, pageable);
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] PatientEncounter constraint violated rootMessage={}", message, exception);

        if (messageLower.contains("ck_patient_encounters_follow_up_required")) {
            return new BadRequestAlertException(
                    "Follow-up encounter is required when reason is FOLLOW_UP (and must be empty otherwise).",
                    "patientEncounter",
                    "followUpEncounter.required.byReason"
            );
        }

        if (messageLower.contains("encounter_number")) {
            return new BadRequestAlertException(
                    "Encounter number already exists.",
                    "patientEncounter",
                    "encounterNumber.duplicate"
            );
        }

        if (messageLower.contains("unique_patient_department_date_encounter")) {
            return new BadRequestAlertException(
                    "This patient already has an encounter for this department on this date.",
                    "patientEncounter",
                    "patient.department.date.duplicate"
            );
        }

        if (messageLower.contains("unique_department_date_sequence_number")) {
            return new BadRequestAlertException(
                    "Department daily sequence number already exists for this date.",
                    "patientEncounter",
                    "department.date.sequence.duplicate"
            );
        }

        if (messageLower.contains("ck_patient_encounters_cancel_only_when_new_or_not_observed")) {
            return new BadRequestAlertException(
                    "Cancel is allowed only when status is NEW OR isObserved is false.",
                    "patientEncounter",
                    "cancel.notAllowed.dbRule"
            );
        }

        if (messageLower.contains("uq_patient_one_ongoing_encounter")) {
            return new BadRequestAlertException(
