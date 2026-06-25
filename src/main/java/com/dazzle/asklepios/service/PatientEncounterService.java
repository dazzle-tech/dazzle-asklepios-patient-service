package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.PractitionerClient;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.domain.AdditionalMeasurements;
import com.dazzle.asklepios.domain.AppointmentFromTemplate;
import com.dazzle.asklepios.domain.BodyMeasurements;
import com.dazzle.asklepios.domain.PainAssessment;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientObservationsComplaints;
import com.dazzle.asklepios.domain.VitalSigns;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import com.dazzle.asklepios.repository.AdditionalMeasurementsRepository;
import com.dazzle.asklepios.repository.AppointmentFromTemplateRepository;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.PainAssessmentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientObservationsComplaintsRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterCreateDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterDischargeDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterSearchFilterDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterUpdateDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.service.helper.PractitionerHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import feign.FeignException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientEncounterService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientEncounterService.class);

    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientRepository patientRepository;
    private final EntityManager entityManager;
    private final EncounterAssignToBedService encounterAssignToBedService;
    private final AdditionalMeasurementsRepository additionalMeasurementsRepository;
    private final PainAssessmentRepository painAssessmentRepository;
    private final VitalSignsRepository vitalSignsRepository;
    private final PatientObservationsComplaintsRepository patientObservationsComplaintsRepository;
    private final BodyMeasurementsRepository bodyMeasurementsRepository;
    private final AppointmentFromTemplateRepository appointmentFromTemplateRepository;
    private final FacilityHelper facilityHelper;
    private final DepartmentHelper departmentHelper;
    private final PractitionerHelper practitionerHelper;
    private final PractitionerClient practitionerClient;

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

        facilityHelper.validateFacilityExists(createDTO.facilityId());
        departmentHelper.validateDepartmentExists(createDTO.departmentId());
        if (createDTO.practitionerId() != null)
            practitionerHelper.validatePractitionerExists(createDTO.practitionerId());

        validateEmergencyEncounterCreation(createDTO.patientId(), createDTO.encounterType());
        PatientEncounter patientEncounterToCreate = PatientEncounter.builder()
                .patient(patient)
                .facilityId(createDTO.facilityId())
                .departmentId(createDTO.departmentId())
                .practitionerId(createDTO.practitionerId())
                .appointment(appointmentFromTemplateRepository.findById(createDTO.appointmentId())
                        .orElseThrow(() -> new NotFoundAlertException(
                                "appointment for this encounter not found with id " + createDTO.appointmentId(),
                                "patientEncounter",
                                "appointment.notfound"
                        ))
                )
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
                .status(EncounterStatus.PENDING_PAYMENT)
                .encounterDate(createDTO.encounterDate())
                .build();

        try {
            PatientEncounter createdPatientEncounter = patientEncounterRepository.saveAndFlush(patientEncounterToCreate);
            entityManager.refresh(createdPatientEncounter); // keep ONLY here (create)
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

        facilityHelper.validateFacilityExists(updateDTO.facilityId());
        departmentHelper.validateDepartmentExists(updateDTO.departmentId());
        if (updateDTO.practitionerId() != null)
            practitionerHelper.validatePractitionerExists(updateDTO.practitionerId());

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
        existingPatientEncounter.setPhysicalExaminationSummery(updateDTO.physicalExaminationSummery());

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
            entityManager.refresh(updatedPatientEncounter); // keep ONLY here (update)
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

        LocalDate effectiveFrom = filter.fromDate() != null ? filter.fromDate() : today;
        LocalDate effectiveTo = filter.toDate() != null ? filter.toDate() : today;

        List<EncounterStatus> effectiveStatuses =
                (filter.statuses() != null && !filter.statuses().isEmpty())
                        ? filter.statuses()
                        : List.of(EncounterStatus.NEW, EncounterStatus.ONGOING);

        boolean hasPatientName = filter.patientName() != null && !filter.patientName().isBlank();
        boolean hasMrn = filter.mrn() != null && !filter.mrn().isBlank();
        boolean hasChief = filter.chiefComplaint() != null && !filter.chiefComplaint().isBlank();

        LOG.debug(
                "[FILTER] effectiveFrom={} effectiveTo={} statuses={} hasPatientName={} hasMrn={} hasChief={}",
                effectiveFrom, effectiveTo, effectiveStatuses, hasPatientName, hasMrn, hasChief
        );

        Specification<PatientEncounter> spec = (root, query, cb) -> {
            applyFetches(root, query);

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
                                cb.lower(cb.coalesce(root.get("chiefComplaint"), "")),
                                "%" + filter.chiefComplaint().trim().toLowerCase() + "%"
                        )
                );
            }

            if (hasPatientName || hasMrn) {
                Join<PatientEncounter, Patient> patientJoin = root.join("patient", JoinType.INNER);

                if (hasMrn) {
                    predicates.add(cb.equal(patientJoin.get("medicalRecordNumber"), filter.mrn().trim()));
                }

                if (hasPatientName) {
                    String[] tokens = filter.patientName().trim().toLowerCase().split("\\s+");

                    Expression<String> first = cb.lower(cb.coalesce(patientJoin.get("firstName"), ""));
                    Expression<String> second = cb.lower(cb.coalesce(patientJoin.get("secondName"), ""));
                    Expression<String> third = cb.lower(cb.coalesce(patientJoin.get("thirdName"), ""));
                    Expression<String> last = cb.lower(cb.coalesce(patientJoin.get("lastName"), ""));

                    Predicate[] tokenPredicates = Arrays.stream(tokens)
                            .filter(token -> token != null && !token.isBlank())
                            .map(token -> {
                                String like = "%" + token + "%";
                                return cb.or(
                                        cb.like(first, like),
                                        cb.like(second, like),
                                        cb.like(third, like),
                                        cb.like(last, like)
                                );
                            })
                            .toArray(Predicate[]::new);

                    if (tokenPredicates.length > 0) {
                        predicates.add(cb.and(tokenPredicates));
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<PatientEncounter> result = patientEncounterRepository.findAll(spec, pageable);

        LOG.debug("[FILTER] PatientEncounters result totalElements={} totalPages={} pageNumber={} pageSize={}", result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());

        return result;
    }

    private void applyFetches(Root<PatientEncounter> root, CriteriaQuery<?> query) {
        if (query == null || query.getResultType() == null) {
            return;
        }

        boolean isCountQuery =
                Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());

        if (!isCountQuery) {
            root.fetch("patient", JoinType.LEFT);
            root.fetch("appointment", JoinType.LEFT);
            query.distinct(true);
        }
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

        if (EncounterStatus.ONGOING.equals(encounter.getStatus())) {
            throw new BadRequestAlertException(
                    "Encounter is already ongoing",
                    "patientEncounter",
                    "encounter.alreadyOngoing"
            );
        }

        String username = currentUsername();
        validateStartedByIsDoctor(username);

        encounter.setStatus(EncounterStatus.ONGOING);
        encounter.setStartedBy(username);
        encounter.setStartedDate(Instant.now());

        try {
            PatientEncounter saved = patientEncounterRepository.saveAndFlush(encounter);
            updateAppointmentStatusForEncounter(
                    AppointmentStatus.IN_SERVICE,
                    saved.getAppointment().getId()
            );
            LOG.info("[START] success id={} status={}", saved.getId(), saved.getStatus());
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[START] failed (constraint) id={}", encounterId, ex);
            throw handleConstraintViolation(ex);
        }
    }

    private void validateStartedByIsDoctor(String login) {
        PractitionerDTO practitioner;

        try {
            practitioner = practitionerClient.resolvePractitioner(null, login);
        } catch (FeignException.NotFound ex) {
            throw new BadRequestAlertException(
                    "Current user is not linked to a practitioner",
                    "patientEncounter",
                    "startedBy.practitioner.notFound"
            );
        } catch (FeignException ex) {
            throw new BadRequestAlertException(
                    "Unable to validate current user practitioner",
                    "patientEncounter",
                    "startedBy.practitioner.validationFailed"
            );
        }

        if (practitioner == null || isBlank(practitioner.jobRole())) {
            throw new BadRequestAlertException(
                    "Current user is not a doctor",
                    "patientEncounter",
                    "startedBy.notDoctor"
            );
        }

        String jobRole = practitioner.jobRole().trim().toUpperCase();

        boolean isDoctor = switch (jobRole) {
            case "PHYSICIAN",
                 "GENERAL_PRACTITIONER",
                 "SPECIALIST",
                 "ANESTHESIOLOGIST",
                 "RADIOLOGIST",
                 "PATHOLOGIST",
                 "PSYCHIATRIST" -> true;
            default -> false;
        };

        if (!isDoctor) {
            throw new BadRequestAlertException(
                    "Current user is not a doctor",
                    "patientEncounter",
                    "startedBy.notDoctor"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public PatientEncounter cancelEncounter(Long encounterId) {
        LOG.info("[CANCEL] PatientEncounter id={}", encounterId);

        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + encounterId,
                        "patientEncounter",
                        "id.notfound"
                ));

        if (!Set.of(
                EncounterStatus.NEW,
                EncounterStatus.WAITING_TRIAGE,
                EncounterStatus.PENDING_PAYMENT
        ).contains(encounter.getStatus())) {

            throw new BadRequestAlertException(
                    "Cancel is allowed only when status is NEW, WAITING_TRIAGE, or PENDING_PAYMENT.",
                    "patientEncounter",
                    "cancel.notAllowed.rule"
            );
        }
        boolean hasObservation = !findEncounterIdsWithObservation(List.of(encounterId)).isEmpty();

        if (hasObservation) {
            throw new BadRequestAlertException(
                    "Cannot cancel encounter with observations.",
                    "patientEncounter",
                    "cancel.notAllowed.hasObservation"
            );
        }

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

        if (encounter.getStatus() != EncounterStatus.ONGOING && encounter.getStatus() != EncounterStatus.TRIAGE_STARTED) {
            throw new BadRequestAlertException(
                    "Complete allowed only when status is ONGOING.",
                    "patientEncounter",
                    "complete.notAllowed"
            );
        }

        encounter.setStatus(EncounterStatus.CLOSED);

        PatientEncounter saved = patientEncounterRepository.saveAndFlush(encounter);
        updateAppointmentStatusForEncounter(AppointmentStatus.COMPLETED, encounter.getAppointment().getId());
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

    @Transactional(readOnly = true)
    public PatientEncounter getById(Long encounterId) {
        LOG.debug("[GET_BY_ID] encounterId={}", encounterId);
        return patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> {
                    LOG.warn("[GET_BY_ID] PatientEncounter not found id={}", encounterId);
                    return new NotFoundAlertException(
                            "PatientEncounter not found with id " + encounterId,
                            "patientEncounter",
                            "id.notfound"
                    );
                });
    }

    private void validateEmergencyEncounterCreation(Long patientId, Object encounterType) {
        if (encounterType == null || !"EMERGENCY".equals(encounterType.toString())) {
            return;
        }
    }

    @Transactional(readOnly = true)
    public PatientEncounter getEncountersByAppointmentId(Long appointmentId) {
        LOG.debug("[GET_BY_APPOINTMENT_ID] appointmentId={} ", appointmentId);
        return patientEncounterRepository.findByAppointment_Id(appointmentId);
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

        if (messageLower.contains("ck_patient_encounters_cancel_only_when_allowed_status")) {
            return new BadRequestAlertException(
                    "Cancel is allowed only when status is NEW, WAITING_TRIAGE, or PENDING_PAYMENT.",
                    "patientEncounter",
                    "cancel.notAllowed.dbRule"
            );
        }

        if (messageLower.contains("ck_patient_encounters_discharge_fields_required")) {
            return new BadRequestAlertException(
                    "Discharge type and discharge date are required when status is DISCHARGED.",
                    "patientEncounter",
                    "discharge.fields.required"
            );
        }

        if (messageLower.contains("ck_patient_encounters_discharge_fields_only_when_discharged")) {
            return new BadRequestAlertException(
                    "Discharge type and discharge date can be filled only when status is DISCHARGED.",
                    "patientEncounter",
                    "discharge.fields.onlyWhenDischarged"
            );
        }

        if (messageLower.contains("patient already has an ongoing encounter. cannot create a new emergency encounter.")) {
            return new BadRequestAlertException(
                    "Patient currently treated by another doctor",
                    "patientEncounter",
                    "patient.emergency.notAllowed.withOngoing"
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
                    "Patient already has same department encounter Today",
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

        return new BadRequestAlertException(
                "Database constraint violated while saving patient encounter.",
                "patientEncounter",
                "db.constraint"
        );
    }

    @Transactional(readOnly = true)
    public long countDepartmentEncountersByDateRange(
            Long departmentId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        LOG.debug("[COUNT_DEPARTMENT_ENCOUNTERS] departmentId={} fromDate={} toDate={}",
                departmentId, fromDate, toDate);

        long total = patientEncounterRepository.countByDepartmentIdAndEncounterDateBetween(
                departmentId,
                fromDate,
                toDate
        );

        LOG.debug("[COUNT_DEPARTMENT_ENCOUNTERS_RESULT] departmentId={} fromDate={} toDate={} total={}",
                departmentId, fromDate, toDate, total);

        return total;
    }


    @Transactional(readOnly = true)
    public long countDepartmentWaitingListPatients(
            Long departmentId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        LOG.debug("[COUNT_WAITING_LIST] departmentId={} fromDate={} toDate={}",
                departmentId, fromDate, toDate);

        long total = patientEncounterRepository.countByDepartmentIdAndEncounterDateBetweenAndStatus(
                departmentId,
                fromDate,
                toDate,
                EncounterStatus.WAITING_LIST
        );

        LOG.debug("[COUNT_WAITING_LIST_RESULT] departmentId={} fromDate={} toDate={} total={}",
                departmentId, fromDate, toDate, total);

        return total;
    }

    @Transactional(readOnly = true)
    public long countDepartmentInTriagePatients(
            Long departmentId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        LOG.debug("[COUNT_TRIAGE_LIST] departmentId={} fromDate={} toDate={}",
                departmentId, fromDate, toDate);

        long total = patientEncounterRepository.countByDepartmentIdAndEncounterDateBetweenAndStatusIn(
                departmentId,
                fromDate,
                toDate,
                List.of(
                        EncounterStatus.WAITING_TRIAGE,
                        EncounterStatus.TRIAGE_STARTED
                )
        );

        LOG.debug("[COUNT_TRIAGE_LIST_RESULT] departmentId={} fromDate={} toDate={} total={}",
                departmentId, fromDate, toDate, total);

        return total;
    }

    @Transactional(readOnly = true)
    public long countDepartmentDischargedPatients(
            Long departmentId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        LOG.debug("[COUNT_DISCHARGED] departmentId={} fromDate={} toDate={}",
                departmentId, fromDate, toDate);

        long total = patientEncounterRepository.countByDepartmentIdAndEncounterDateBetweenAndStatus(
                departmentId,
                fromDate,
                toDate,
                EncounterStatus.DISCHARGED
        );

        LOG.debug("[COUNT_DISCHARGED_RESULT] departmentId={} fromDate={} toDate={} total={}",
                departmentId, fromDate, toDate, total);

        return total;
    }

    public PatientEncounter moveToNew(Long id) {
        LOG.info("[STATUS CHANGE] Move encounter to NEW id={}", id);

        PatientEncounter encounter = patientEncounterRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient encounter not found with id " + id,
                        "patientEncounter",
                        "notfound"
                ));

        EncounterStatus currentStatus = encounter.getStatus();

        if (!(currentStatus == EncounterStatus.WAITING_LIST
                || currentStatus == EncounterStatus.TRIAGE_STARTED)) {
            LOG.warn("[STATUS CHANGE] Invalid transition for id={} currentStatus={}",
                    id, currentStatus);

            throw new BadRequestAlertException(
                    "Only encounters in WAITING_LIST or TRIAGE_STARTED can be moved to NEW.",
                    "patientEncounter",
                    "invalid.status.transition"
            );
        }

        encounter.setStatus(EncounterStatus.NEW);

        PatientEncounter updated = patientEncounterRepository.saveAndFlush(encounter);

        LOG.info("[STATUS CHANGE] Successfully moved id={} to NEW", id);

        return updated;
    }

    public PatientEncounter dischargeEncounter(PatientEncounterDischargeDTO dischargeDTO) {
        LOG.info("[DISCHARGE] PatientEncounter payload={}", dischargeDTO);

        PatientEncounter encounter = patientEncounterRepository.findById(dischargeDTO.encounterId())
                .orElseThrow(() -> {
                    LOG.warn("[DISCHARGE] PatientEncounter rejected: not found id={}", dischargeDTO.encounterId());
                    return new NotFoundAlertException(
                            "PatientEncounter not found with id " + dischargeDTO.encounterId(),
                            "patientEncounter",
                            "id.notfound"
                    );
                });

        if (encounter.getStatus() != EncounterStatus.ONGOING) {
            LOG.warn("[DISCHARGE] PatientEncounter rejected: invalid status id={} status={}",
                    dischargeDTO.encounterId(), encounter.getStatus());
            throw new BadRequestAlertException(
                    "Discharge allowed only when status is ONGOING.",
                    "patientEncounter",
                    "discharge.notAllowed"
            );
        }

        if (dischargeDTO.dischargeType() == null) {
            LOG.warn("[DISCHARGE] PatientEncounter rejected: dischargeType is null id={}", dischargeDTO.encounterId());
            throw new BadRequestAlertException(
                    "Discharge type is required.",
                    "patientEncounter",
                    "discharge.type.required"
            );
        }

        if (dischargeDTO.dischargeAt() == null) {
            LOG.warn("[DISCHARGE] PatientEncounter rejected: dischargeAt is null id={}", dischargeDTO.encounterId());
            throw new BadRequestAlertException(
                    "Discharge date and time are required.",
                    "patientEncounter",
                    "discharge.at.required"
            );
        }

        encounter.setDischargeType(dischargeDTO.dischargeType());
        encounter.setDischargeAt(dischargeDTO.dischargeAt());
        encounter.setStatus(EncounterStatus.DISCHARGED);

        try {
            PatientEncounter saved = patientEncounterRepository.saveAndFlush(encounter);

            encounterAssignToBedService.dischargeActiveAssignmentByEncounterId(saved.getId());

            LOG.info("[DISCHARGE] success id={} status={} dischargeType={} dischargeAt={}",
                    saved.getId(),
                    saved.getStatus(),
                    saved.getDischargeType(),
                    saved.getDischargeAt());

            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[DISCHARGE] failed (constraint) payload={}", dischargeDTO, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[DISCHARGE] failed (unexpected) payload={}", dischargeDTO, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Optional<PatientEncounter> getPreviousClosedEncounter(Long encounterId) {
        PatientEncounter currentEncounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + encounterId,
                        "patientEncounter",
                        "id.notfound"
                ));

        return patientEncounterRepository
                .findFirstByPatientIdAndStatusAndEncounterDateLessThanEqualOrderByEncounterDateDesc(
                        currentEncounter.getPatient().getId(),
                        EncounterStatus.CLOSED,
                        currentEncounter.getEncounterDate()
                );
    }

    public Set<Long> findEncounterIdsWithObservation(List<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> result = new HashSet<>();

        // AdditionalMeasurements
        result.addAll(
                additionalMeasurementsRepository.findDistinctByEncounterIdIn(encounterIds)
                        .stream()
                        .map(AdditionalMeasurements::getEncounterId)
                        .collect(Collectors.toSet())
        );

        // VitalSigns
        result.addAll(
                vitalSignsRepository.findDistinctByEncounterIdIn(encounterIds)
                        .stream()
                        .map(VitalSigns::getEncounterId)
                        .collect(Collectors.toSet())
        );

        // NursingNote
        result.addAll(
                painAssessmentRepository.findDistinctByEncounterIdIn(encounterIds)
                        .stream()
                        .map(PainAssessment::getEncounterId)
                        .collect(Collectors.toSet())
        );

        // IntakeOutput
        result.addAll(
                bodyMeasurementsRepository.findDistinctByEncounterIdIn(encounterIds)
                        .stream()
                        .map(BodyMeasurements::getEncounterId)
                        .collect(Collectors.toSet())
        );

        // ObservationAttachment
        result.addAll(
                patientObservationsComplaintsRepository.findDistinctByEncounterIdIn(encounterIds)
                        .stream()
                        .map(PatientObservationsComplaints::getEncounterId)
                        .collect(Collectors.toSet())
        );

        return result;
    }

    public void updateAppointmentStatusForEncounter(
            AppointmentStatus status,
            Long appointmentId

    ) {
        LOG.debug("update Appointment Status From Encounter for status={}", status);
        AppointmentFromTemplate appointment = appointmentFromTemplateRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Appointment for this encounter not found with id " + appointmentId,
                        "patientEncounter",
                        "appointment.notfound"
                ));

        appointment.setStatus(AppointmentStatus.IN_SERVICE);
        appointmentFromTemplateRepository.save(appointment);
    }

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (username == null) {
            LOG.warn("[ConsultationPortalService] AUTH - unauthenticated request");
            throw new BadRequestAlertException(
                    "unauthenticated",
                    "consultation",
                    "No authenticated user"
            );
        }
        return username;
    }

    public List<PatientEncounter> getEncountersByIds(List<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return List.of();
        }

        return patientEncounterRepository.findAllById(encounterIds);
    }
}