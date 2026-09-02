package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.PractitionerClient;
import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.FacilityDTO;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.domain.AdditionalMeasurements;
import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.BodyMeasurements;
import com.dazzle.asklepios.domain.EncounterDischargeLog;
import com.dazzle.asklepios.domain.PainAssessment;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientObservationsComplaints;
import com.dazzle.asklepios.domain.User;
import com.dazzle.asklepios.domain.VitalSigns;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import com.dazzle.asklepios.repository.AdditionalMeasurementsRepository;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.EncounterDischargeLogRepository;
import com.dazzle.asklepios.repository.PainAssessmentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientObservationsComplaintsRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.UserRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.EncounterHasInvoiceResponse;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterCreateDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterDischargeDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterSearchFilterDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterUpdateDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.service.helper.NotificationHelper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
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
    private final AppointmentRepository appointmentRepository;
    private final FacilityHelper facilityHelper;
    private final DepartmentHelper departmentHelper;
    private final PractitionerHelper practitionerHelper;
    private final PractitionerClient practitionerClient;
    private final BillingEngineService billingEngineService;
    private final NotificationHelper notificationHelper;
    private final InvoiceGenerationService invoiceGenerationService;
    private final UserRepository userRepository;
    private final EncounterDischargeLogRepository encounterDischargeLogRepository;

    public PatientEncounterService(
            PatientEncounterRepository patientEncounterRepository,
            PatientRepository patientRepository,
            EntityManager entityManager,
            EncounterAssignToBedService encounterAssignToBedService,
            AdditionalMeasurementsRepository additionalMeasurementsRepository,
            PainAssessmentRepository painAssessmentRepository,
            VitalSignsRepository vitalSignsRepository,
            PatientObservationsComplaintsRepository patientObservationsComplaintsRepository,
            BodyMeasurementsRepository bodyMeasurementsRepository, AppointmentRepository appointmentRepository,
            FacilityHelper facilityHelper,
            DepartmentHelper departmentHelper,
            PractitionerHelper practitionerHelper,
            PractitionerClient practitionerClient,
            @Lazy BillingEngineService billingEngineService,
            NotificationHelper notificationHelper, InvoiceGenerationService invoiceGenerationService, UserRepository userRepository, EncounterDischargeLogRepository encounterDischargeLogRepository) {
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientRepository = patientRepository;
        this.entityManager = entityManager;
        this.encounterAssignToBedService = encounterAssignToBedService;
        this.additionalMeasurementsRepository = additionalMeasurementsRepository;
        this.painAssessmentRepository = painAssessmentRepository;
        this.vitalSignsRepository = vitalSignsRepository;
        this.patientObservationsComplaintsRepository = patientObservationsComplaintsRepository;
        this.bodyMeasurementsRepository = bodyMeasurementsRepository;
        this.appointmentRepository = appointmentRepository;
        this.facilityHelper = facilityHelper;
        this.departmentHelper = departmentHelper;
        this.practitionerHelper = practitionerHelper;
        this.practitionerClient = practitionerClient;
        this.billingEngineService = billingEngineService;
        this.notificationHelper = notificationHelper;
        this.invoiceGenerationService = invoiceGenerationService;
        this.userRepository = userRepository;
        this.encounterDischargeLogRepository = encounterDischargeLogRepository;
    }

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
        Appointment appointment = appointmentRepository.findById(createDTO.appointmentId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "appointment for this encounter not found with id " + createDTO.appointmentId(),
                        "patientEncounter",
                        "appointment.notfound"
                ));
        PatientEncounter patientEncounterToCreate = PatientEncounter.builder()
                .patient(patient)
                .facilityId(createDTO.facilityId())
                .departmentId(createDTO.departmentId())
                .practitionerId(createDTO.practitionerId())
                .appointment(appointmentRepository.findById(createDTO.appointmentId())
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
                .encounterDate(createDTO.encounterDate())
                .encounterTime(
                        appointment.getStartDatetime() != null
                                ? appointment.getStartDatetime().atZone(ZoneId.systemDefault()).toLocalTime()
                                : LocalTime.now()
                )
                .status(TreatmentStatus.PENDING_PAYMENT)
                .encounterDate(createDTO.encounterDate())
                .encounterTime(createDTO.encounterTime()).build();

        try {
            PatientEncounter createdPatientEncounter = patientEncounterRepository.saveAndFlush(patientEncounterToCreate);
            entityManager.refresh(createdPatientEncounter); // keep ONLY here (create)
            if (createdPatientEncounter.getFollowUpEncounter() != null) {
                createdPatientEncounter.getFollowUpEncounter().getCreatedDate();
            }
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

        applyTreatmentStatusUpdate(
                existingPatientEncounter,
                updateDTO.status()
        );

        try {
            PatientEncounter updatedPatientEncounter = patientEncounterRepository.saveAndFlush(existingPatientEncounter);
            entityManager.refresh(updatedPatientEncounter); // keep ONLY here (update)
            LOG.info("[UPDATE] PatientEncounter success id={} patientId={} departmentId={} status={}",
                    updatedPatientEncounter.getId(),
                    updateDTO.patientId(),
                    updateDTO.departmentId(),
                    updatedPatientEncounter.getStatus()
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

    public PatientEncounter StartTriageEncounter(Long patientEncounterId) {
        LOG.info("[UPDATE] PatientEncounter id={}", patientEncounterId);

        PatientEncounter existingPatientEncounter = patientEncounterRepository.findById(patientEncounterId)
                .orElseThrow(() -> {
                    LOG.warn("[UPDATE] PatientEncounter rejected: not found id={}", patientEncounterId);
                    return new NotFoundAlertException(
                            "id.notfound",
                            "patientEncounter",
                            "PatientEncounter not found with id " + patientEncounterId
                    );
                });
        if (existingPatientEncounter.getStatus() == TreatmentStatus.TRIAGE_STARTED) {

            throw new BadRequestAlertException(
                    "triage.alreadyStarted",
                    "patientEncounter",
                    "Triage already started for this encounter"
            );
        } else if (existingPatientEncounter.getStatus() != TreatmentStatus.WAITING_TRIAGE) {
            throw new BadRequestAlertException(
                    "triage.notAllowed",
                    "patientEncounter",
                    "Triage can only be started when status is WAITING_TRIAGE."
            );
        }
        existingPatientEncounter.setStatus(TreatmentStatus.TRIAGE_STARTED);


        try {
            PatientEncounter updatedPatientEncounter = patientEncounterRepository.saveAndFlush(existingPatientEncounter);
            LOG.info("[Start Triage] PatientEncounter success id={} patientId={} departmentId={} status={}",
                    updatedPatientEncounter.getId(),
                    updatedPatientEncounter.getPatient().getId(),
                    updatedPatientEncounter.getDepartmentId(),
                    updatedPatientEncounter.getStatus()
            );
            return updatedPatientEncounter;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[UPDATE] PatientEncounter failed (constraint) id={}", patientEncounterId, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[UPDATE] PatientEncounter failed (unexpected) id={} ", patientEncounterId, ex);
            throw ex;
        }
    }


    @Transactional(readOnly = true)
    public Page<PatientEncounter> filterEncounters(PatientEncounterSearchFilterDTO filter, Pageable pageable) {
        LOG.debug("Service filter PatientEncounters filter={} pageable={}", filter, pageable);

        LocalDate today = LocalDate.now();

        LocalDate effectiveFrom = filter.fromDate() != null ? filter.fromDate() : today;
        LocalDate effectiveTo = filter.toDate() != null ? filter.toDate() : today;

        List<TreatmentStatus> effectiveStatuses =
                (filter.statuses() != null && !filter.statuses().isEmpty())
                        ? filter.statuses()
                        : List.of(TreatmentStatus.NEW, TreatmentStatus.ONGOING);

        boolean hasPatientName = filter.patientName() != null && !filter.patientName().isBlank();
        boolean hasMrn = filter.mrn() != null && !filter.mrn().isBlank();
        boolean hasChief = filter.chiefComplaint() != null && !filter.chiefComplaint().isBlank();

        LOG.debug(
                "[FILTER] effectiveFrom={} effectiveTo={} treatmentStatuses={} hasPatientName={} hasMrn={} hasChief={}",
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

            if (filter.practitionerId() != null) {
                predicates.add(cb.equal(root.get("practitionerId"), filter.practitionerId()));
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
        List<TreatmentStatus> completedEncounterStatuses = List.of(
                TreatmentStatus.COMPLETED,
                TreatmentStatus.DISCHARGED
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

        if (TreatmentStatus.ONGOING.equals(encounter.getStatus())) {
            throw new BadRequestAlertException(
                    "Encounter is already ongoing",
                    "patientEncounter",
                    "encounter.alreadyOngoing");

        }

        String username = currentUsername();
        validateStartedByIsDoctor(username);
        encounter.setStatus(TreatmentStatus.ONGOING);
        encounter.setStartedBy(currentUsername());
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
                        "id.notfound",
                        "patientEncounter",
                        "PatientEncounter not found with id " + encounterId
                ));

        if (!Set.of(
                TreatmentStatus.NEW,
                TreatmentStatus.WAITING_TRIAGE,
                TreatmentStatus.PENDING_PAYMENT
        ).contains(encounter.getStatus())) {
            throw new BadRequestAlertException(
                    "cancel.notAllowed.rule",
                    "patientEncounter",
                    "Cancel is allowed only when status is NEW, WAITING_TRIAGE, or PENDING_PAYMENT."
            );
        }
        boolean hasObservation = !findEncounterIdsWithObservation(List.of(encounterId)).isEmpty();

        if (hasObservation) {
            throw new BadRequestAlertException(
                    "cancel.notAllowed.hasObservation",
                    "patientEncounter",
                    "Cannot cancel encounter with observations."
            );
        }

        billingEngineService.cancelEncounter(
                encounterId,
                "Clinical encounter cancelled",
                "ENCOUNTER-CANCEL:" + encounterId
        );

        encounter.setStatus(TreatmentStatus.CANCELLED);
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

        if (encounter.getStatus() != TreatmentStatus.ONGOING) {
            throw new BadRequestAlertException(
                    "Discharge allowed only when status is ONGOING.",
                    "patientEncounter",
                    "discharge.notAllowed"
            );
        }

        encounter.setStatus(TreatmentStatus.DISCHARGED);

        PatientEncounter saved = patientEncounterRepository.saveAndFlush(encounter);
        LOG.info("[DISCHARGE] success id={} status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    @Transactional
    public PatientEncounter completeEncounter(Long encounterId) {
        LOG.info("[COMPLETE] PatientEncounter id={}", encounterId);

        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + encounterId,
                        "patientEncounter",
                        "id.notfound"
                ));

        if (encounter.getStatus() != TreatmentStatus.NEW
                && encounter.getStatus() != TreatmentStatus.ONGOING
                && encounter.getStatus() != TreatmentStatus.TRIAGE_STARTED) {

            LOG.warn("[COMPLETE] PatientEncounter rejected: invalid status id={} status={}",
                    encounterId,
                    encounter.getStatus());

            throw new BadRequestAlertException(
                    "Complete allowed only when status is NEW, ONGOING, or TRIAGE_STARTED.",
                    "patientEncounter",
                    "complete.notAllowed"
            );
        }

        /*
         * Update encounter completion details
         */
        encounter.setStatus(TreatmentStatus.COMPLETED);
        encounter.setCompletedAt(Instant.now());
        encounter.setCompletedBy(
                SecurityUtils.getCurrentUserLogin().orElse("system")
        );

        try {
            /*
             * Save encounter
             */
            PatientEncounter saved = patientEncounterRepository.saveAndFlush(encounter);

            /*
             * Create completion log
             */
            LocalDateTime dischargedAt = saved.getCompletedAt().atZone(ZoneId.systemDefault()).toLocalDateTime();
            EncounterDischargeLog completionLog = EncounterDischargeLog.builder()
                    .encounter(saved)
                    .dischargedAt(dischargedAt)
                    .dischargedBy(currentUsername())
                    .createdDate(Instant.now())

                    .build();

            encounterDischargeLogRepository.save(completionLog);

            /*
             * Update appointment
             */
            if (saved.getAppointment() != null) {
                updateAppointmentStatusForEncounter(
                        AppointmentStatus.COMPLETED,
                        saved.getAppointment().getId()
                );
            }

            /*
             * Notify encounter closed
             */
            notifyEncounterEvent(
                    saved,
                    NotificationCode.ENCOUNTER_CLOSED,
                    null
            );

            LOG.info(
                    "[COMPLETE] success id={} status={} completedAt={} completedBy={}",
                    saved.getId(),
                    saved.getStatus(),
                    saved.getCompletedAt(),
                    saved.getCompletedBy()
            );

            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[COMPLETE] failed (constraint) id={}", encounterId, ex);
            throw handleConstraintViolation(ex);

        } catch (RuntimeException ex) {
            LOG.error("[COMPLETE] failed (unexpected) id={}", encounterId, ex);
            throw ex;
        }
    }
    @Transactional
    public PatientEncounter reopenEncounter(Long encounterId) {

        LOG.info("[REOPEN] PatientEncounter id={}", encounterId);

        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + encounterId,
                        "patientEncounter",
                        "id.notfound"
                ));

        User user = userRepository.findByLogin(currentUsername())
                .orElseThrow(() -> new NotFoundAlertException(
                        "User not found with login " + currentUsername(),
                        "patientEncounter",
                        "user.notfound"
                ));

        TreatmentStatus currentStatus = encounter.getStatus();

        if (currentStatus != TreatmentStatus.COMPLETED
                && currentStatus != TreatmentStatus.DISCHARGED) {

            throw new BadRequestAlertException(
                    "Only completed or discharged encounters can be reopened.",
                    "patientEncounter",
                    "reopen.notAllowed"
            );
        }

       EncounterHasInvoiceResponse invoiceResponse =
                invoiceGenerationService.hasInvoice(encounterId);

        if (invoiceResponse != null && invoiceResponse.hasInvoice()) {

            throw new BadRequestAlertException(
                    "Cannot reopen encounter because an invoice has already been generated.",
                    "patientEncounter",
                    "reopen.invoiceExists"
            );
        }

        if (encounter.getEncounterType() == EncounterType.CLINIC
                && !user.isCanUnCompleteEncounter()) {

            throw new BadRequestAlertException(
                    "You do not have permission to reopen outpatient encounters.",
                    "patientEncounter",
                    "reopen.permissionDenied"
            );
        }

        if (encounter.getEncounterType() == EncounterType.EMERGENCY
                && !user.isCanUnDischargeUrgentCare()) {

            throw new BadRequestAlertException(
                    "You do not have permission to reopen emergency encounters.",
                    "patientEncounter",
                    "reopen.permissionDenied"
            );
        }

        encounter.setStatus(TreatmentStatus.ONGOING);

        encounter.setCompletedAt(null);
        encounter.setCompletedBy(null);

        encounter.setDischargeAt(null);
        encounter.setDischargeType(null);

        PatientEncounter saved =
                patientEncounterRepository.saveAndFlush(encounter);

        LOG.info(
                "[REOPEN] success id={} fromStatus={} toStatus={}",
                saved.getId(),
                currentStatus,
                saved.getStatus()
        );

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
                List.of(TreatmentStatus.NEW, TreatmentStatus.ONGOING)
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
                        TreatmentStatus.COMPLETED
                );

        LOG.debug("[DASHBOARD] COUNT_COMPLETED_RESULT departmentId={} date={} total={}",
                departmentId, today, completed);

        return completed;
    }

    @Transactional(readOnly = true)
    public Page<PatientEncounter> searchEncounters(
            Long facilityId,
            PatientEncounterSearchFilterDTO filter,
            Pageable pageable
    ) {
        LOG.debug(
                "[SEARCH] PatientEncounters facilityId={} departmentId={} filter={} pageable={}",
                facilityId,
                filter != null ? filter.departmentId() : null,
                filter,
                pageable
        );

        LocalDate today = LocalDate.now();

        LocalDate effectiveFrom =
                filter != null && filter.fromDate() != null
                        ? filter.fromDate()
                        : today;

        LocalDate effectiveTo =
                filter != null && filter.toDate() != null
                        ? filter.toDate()
                        : today;

        List<TreatmentStatus> effectiveStatuses =
                filter != null
                        && filter.statuses() != null
                        && !filter.statuses().isEmpty()
                        ? filter.statuses()
                        : null;

        boolean hasPatientName =
                filter != null
                        && filter.patientName() != null
                        && !filter.patientName().isBlank();


        boolean hasEncounterNumber =
                filter != null
                        && filter.encounterNumber() != null
                        && !filter.encounterNumber().isBlank();


        boolean hasMrn =
                filter != null
                        && filter.mrn() != null
                        && !filter.mrn().isBlank();

        boolean hasChief =
                filter != null
                        && filter.chiefComplaint() != null
                        && !filter.chiefComplaint().isBlank();

        Long departmentId =
                filter != null
                        ? filter.departmentId()
                        : null;

        Long practitionerId =
                filter != null
                        ? filter.practitionerId()
                        : null;

        Specification<PatientEncounter> spec = (root, query, cb) -> {

            applyFetches(root, query);

            List<Predicate> predicates = new ArrayList<>();

            /*
             * Facility is OPTIONAL
             *
             * facilityId != null
             *     -> filter by facility
             *
             * facilityId == null
             *     -> don't filter facility
             */
            if (facilityId != null) {
                predicates.add(
                        cb.equal(
                                root.get("facilityId"),
                                facilityId
                        )
                );
            }

            if (departmentId != null) {
                predicates.add(
                        cb.equal(
                                root.get("departmentId"),
                                departmentId
                        )
                );
            }

            if (practitionerId != null) {
                predicates.add(
                        cb.equal(
                                root.get("practitionerId"),
                                practitionerId
                        )
                );
            }

            if (hasEncounterNumber) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("encounterNumber")),
                                "%" + filter.encounterNumber().trim().toLowerCase() + "%"
                        )
                );
            }

            predicates.add(
                    cb.between(
                            root.get("encounterDate"),
                            effectiveFrom,
                            effectiveTo
                    )
            );

            if (effectiveStatuses != null && !effectiveStatuses.isEmpty()) {
                predicates.add(
                        root.get("status").in(effectiveStatuses)
                );
            }

            if (
                    filter != null
                            && filter.encounterReasons() != null
                            && !filter.encounterReasons().isEmpty()
            ) {
                predicates.add(
                        root.get("encounterReason")
                                .in(filter.encounterReasons())
                );
            }

            if (
                    filter != null
                            && filter.priorities() != null
                            && !filter.priorities().isEmpty()
            ) {
                predicates.add(
                        root.get("priorityLevel")
                                .in(filter.priorities())
                );
            }

            if (hasChief) {
                predicates.add(
                        cb.like(
                                cb.lower(
                                        cb.coalesce(
                                                root.get("chiefComplaint"),
                                                ""
                                        )
                                ),
                                "%" +
                                        filter.chiefComplaint()
                                                .trim()
                                                .toLowerCase() +
                                        "%"
                        )
                );
            }

            if (hasPatientName || hasMrn) {

                Join<PatientEncounter, Patient> patientJoin =
                        root.join(
                                "patient",
                                JoinType.INNER
                        );

                if (hasMrn) {
                    predicates.add(
                            cb.equal(
                                    patientJoin.get(
                                            "medicalRecordNumber"
                                    ),
                                    filter.mrn().trim()
                            )
                    );
                }

                if (hasPatientName) {

                    String[] tokens =
                            filter.patientName()
                                    .trim()
                                    .toLowerCase()
                                    .split("\\s+");

                    Expression<String> first =
                            cb.lower(
                                    cb.coalesce(
                                            patientJoin.get("firstName"),
                                            ""
                                    )
                            );

                    Expression<String> second =
                            cb.lower(
                                    cb.coalesce(
                                            patientJoin.get("secondName"),
                                            ""
                                    )
                            );

                    Expression<String> third =
                            cb.lower(
                                    cb.coalesce(
                                            patientJoin.get("thirdName"),
                                            ""
                                    )
                            );

                    Expression<String> last =
                            cb.lower(
                                    cb.coalesce(
                                            patientJoin.get("lastName"),
                                            ""
                                    )
                            );

                    Predicate[] tokenPredicates =
                            Arrays.stream(tokens)
                                    .filter(
                                            token ->
                                                    token != null
                                                            && !token.isBlank()
                                    )
                                    .map(token -> {

                                        String like =
                                                "%" + token + "%";

                                        return cb.or(
                                                cb.like(first, like),
                                                cb.like(second, like),
                                                cb.like(third, like),
                                                cb.like(last, like)
                                        );
                                    })
                                    .toArray(Predicate[]::new);

                    if (tokenPredicates.length > 0) {
                        predicates.add(
                                cb.and(tokenPredicates)
                        );
                    }
                }
            }

            return cb.and(
                    predicates.toArray(
                            new Predicate[0]
                    )
            );
        };

        Page<PatientEncounter> result =
                patientEncounterRepository.findAll(
                        spec,
                        pageable
                );

        LOG.debug(
                "[SEARCH] PatientEncounters result totalElements={} totalPages={} pageNumber={} pageSize={}",
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );

        return result;
    }

    @Transactional(readOnly = true)
    public long countTodayDepartmentCancelled(Long departmentId) {
        LocalDate today = LocalDate.now();

        LOG.debug("[DASHBOARD] COUNT_CANCELLED departmentId={} date={}", departmentId, today);

        long cancelled = patientEncounterRepository
                .countByDepartmentIdAndEncounterDateAndStatus(
                        departmentId,
                        today,
                        TreatmentStatus.CANCELLED
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
                TreatmentStatus.WAITING_LIST
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
                        TreatmentStatus.WAITING_TRIAGE,
                        TreatmentStatus.TRIAGE_STARTED
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
                TreatmentStatus.DISCHARGED
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

        TreatmentStatus currentStatus = encounter.getStatus();

        if (!(currentStatus == TreatmentStatus.WAITING_LIST
                || currentStatus == TreatmentStatus.TRIAGE_STARTED)) {
            LOG.warn("[STATUS CHANGE] Invalid transition for id={} currentStatus={}",
                    id, currentStatus);

            throw new BadRequestAlertException(
                    "Only encounters in WAITING_LIST or TRIAGE_STARTED can be moved to NEW.",
                    "patientEncounter",
                    "invalid.status.transition"
            );
        }
        encounter.setStatus(TreatmentStatus.NEW);
        PatientEncounter updated = patientEncounterRepository.saveAndFlush(encounter);

        LOG.info("[STATUS CHANGE] Successfully moved id={} to NEW", id);

        return updated;
    }

    @Transactional
    public PatientEncounter dischargeEncounter(PatientEncounterDischargeDTO dischargeDTO) {
        LOG.info("[DISCHARGE] PatientEncounter payload={}", dischargeDTO);

        PatientEncounter encounter = patientEncounterRepository.findById(dischargeDTO.encounterId())
                .orElseThrow(() -> {
                    LOG.warn("[DISCHARGE] PatientEncounter rejected: not found id={}",
                            dischargeDTO.encounterId());

                    return new NotFoundAlertException(
                            "PatientEncounter not found with id " + dischargeDTO.encounterId(),
                            "patientEncounter",
                            "id.notfound"
                    );
                });

        if (encounter.getStatus() != TreatmentStatus.ONGOING) {
            LOG.warn("[DISCHARGE] PatientEncounter rejected: invalid status id={} status={}",
                    dischargeDTO.encounterId(),
                    encounter.getStatus());

            throw new BadRequestAlertException(
                    "Discharge allowed only when status is ONGOING.",
                    "patientEncounter",
                    "discharge.notAllowed"
            );
        }

        if (dischargeDTO.dischargeType() == null) {
            LOG.warn("[DISCHARGE] PatientEncounter rejected: dischargeType is null id={}",
                    dischargeDTO.encounterId());

            throw new BadRequestAlertException(
                    "Discharge type is required.",
                    "patientEncounter",
                    "discharge.type.required"
            );
        }

        if (dischargeDTO.dischargeAt() == null) {
            LOG.warn("[DISCHARGE] PatientEncounter rejected: dischargeAt is null id={}",
                    dischargeDTO.encounterId());

            throw new BadRequestAlertException(
                    "Discharge date and time are required.",
                    "patientEncounter",
                    "discharge.at.required"
            );
        }

        /*
         * Update encounter discharge details
         */
        encounter.setDischargeType(dischargeDTO.dischargeType());
        encounter.setDischargeAt(dischargeDTO.dischargeAt());
        encounter.setStatus(TreatmentStatus.DISCHARGED);

        try {
            /*
             * Save the encounter first
             */
            PatientEncounter saved = patientEncounterRepository.saveAndFlush(encounter);

            /*
             * Create discharge log
             */
            EncounterDischargeLog dischargeLog = EncounterDischargeLog.builder()
                    .encounter(saved)
                    .dischargedAt(dischargeDTO.dischargeAt())
                    .dischargedBy(currentUsername())
                    .createdDate(Instant.now())

                    .build();

            encounterDischargeLogRepository.save(dischargeLog);

            /*
             * Discharge active bed assignment
             */
            encounterAssignToBedService
                    .dischargeActiveAssignmentByEncounterId(saved.getId());

            /*
             * Notify encounter closed
             */
            notifyEncounterEvent(
                    saved,
                    NotificationCode.ENCOUNTER_CLOSED,
                    null
            );

            LOG.info(
                    "[DISCHARGE] success id={} status={} dischargeType={} dischargeAt={} dischargedBy={}",
                    saved.getId(),
                    saved.getStatus(),
                    saved.getDischargeType(),
                    saved.getDischargeAt(),
                    dischargeLog.getDischargedBy()
            );

            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[DISCHARGE] failed (constraint) payload={}", dischargeDTO, ex);
            throw handleConstraintViolation(ex);

        } catch (RuntimeException ex) {
            LOG.error("[DISCHARGE] failed (unexpected) payload={}", dischargeDTO, ex);
            throw ex;
        }
    }
    public PatientEncounter closeEncounterForBilling(Long encounterId) {
        LOG.info("[CLOSE_BILLING] PatientEncounter id={}", encounterId);

        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + encounterId,
                        "patientEncounter",
                        "id.notfound"
                ));


        PatientEncounter saved = patientEncounterRepository.saveAndFlush(encounter);
        LOG.info("[CLOSE_BILLING] success id={} encounterStatus={}", saved.getId(), saved.getEncounterStatus());
        return saved;
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
                        TreatmentStatus.COMPLETED,
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
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Appointment for this encounter not found with id " + appointmentId,
                        "patientEncounter",
                        "appointment.notfound"
                ));

        appointment.setStatus(AppointmentStatus.IN_SERVICE);
        appointmentRepository.save(appointment);
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

    /**
     * Applies treatment-status changes from the update payload.
     * Registration payment (including collect-zero / defer collection) moves
     * {@link TreatmentStatus#PENDING_PAYMENT} to {@link TreatmentStatus#WAITING_TRIAGE}
     * for triage encounters, or {@link TreatmentStatus#NEW} otherwise.
     */
    private void applyTreatmentStatusUpdate(
            PatientEncounter encounter,
            TreatmentStatus requestedStatus
    ) {
        if (requestedStatus == null) {
            return;
        }

        TreatmentStatus currentStatus = encounter.getStatus();
        if (requestedStatus.equals(currentStatus)) {
            return;
        }

        if (currentStatus == TreatmentStatus.PENDING_PAYMENT) {
            TreatmentStatus allowedTarget =
                    resolvesToWaitingTriageAfterRegistrationPayment(encounter)
                            ? TreatmentStatus.WAITING_TRIAGE
                            : TreatmentStatus.NEW;

            if (requestedStatus != allowedTarget
                    && requestedStatus != TreatmentStatus.WAITING_TRIAGE
                    && requestedStatus != TreatmentStatus.NEW) {
                throw new BadRequestAlertException(
                        "status.transition.notAllowed",
                        "patientEncounter",
                        "Treatment status cannot move from PENDING_PAYMENT to "
                                + requestedStatus
                );
            }

            encounter.setStatus(requestedStatus);
            LOG.info(
                    "[UPDATE] PatientEncounter treatment status advanced "
                            + "id={} from={} to={}",
                    encounter.getId(),
                    currentStatus,
                    requestedStatus
            );
            return;
        }

        encounter.setStatus(requestedStatus);
    }

    private boolean resolvesToWaitingTriageAfterRegistrationPayment(
            PatientEncounter encounter
    ) {
        return EncounterType.EMERGENCY.equals(encounter.getEncounterType())
                || EncounterReason.URGENT_VISIT.equals(
                encounter.getEncounterReason()
        );
    }

    public List<PatientEncounter> getEncountersByIds(List<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return List.of();
        }

        return patientEncounterRepository.findAllById(encounterIds);
    }

    public PatientEncounter updateHistoryOfPresentIllness(Long id, String historyOfPresentIllness) {
        LOG.debug("REST request to update History of Present Illness for Encounter : {}", id);

        PatientEncounter encounter = patientEncounterRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found",
                        "patientEncounter",
                        "idnotfound"
                ));

        encounter.setHistoryOfPresentIllness(historyOfPresentIllness);

        PatientEncounter saved = patientEncounterRepository.save(encounter);

        LOG.debug("History of Present Illness updated successfully for Encounter : {}", id);

        return saved;
    }

    @Transactional
    public PatientEncounter reassignPractitioner(
            Long encounterId,
            Long newPractitionerId
    ) {
        LOG.info(
                "[REASSIGN_PRACTITIONER] encounterId={} newPractitionerId={}",
                encounterId,
                newPractitionerId
        );

        PatientEncounter encounter =
                patientEncounterRepository.findById(encounterId)
                        .orElseThrow(() -> new NotFoundAlertException(
                                "PatientEncounter not found with id " + encounterId,
                                "patientEncounter",
                                "id.notfound"
                        ));

        if (newPractitionerId == null) {
            throw new BadRequestAlertException(
                    "Practitioner id is required",
                    "patientEncounter",
                    "practitioner.required"
            );
        }
        if (encounter.getStatus() != TreatmentStatus.NEW && encounter.getStatus() != TreatmentStatus.PENDING_PAYMENT) {
            throw new BadRequestAlertException(
                    "Practitioner can only be reassigned when encounter status is NEW or PENDING_PAYMENT",
                    "patientEncounter",
                    "practitioner.reassign.notAllowed.status"
            );
        }

        PractitionerDTO oldPractitioner = null;
        if (encounter.getPractitionerId() != null) {
            oldPractitioner = practitionerHelper.getPractitioner(encounter.getPractitionerId());
        }


        if (oldPractitioner != null && oldPractitioner.id().equals(newPractitionerId)) {
            throw new BadRequestAlertException(
                    "New practitioner must be different from the current practitioner",
                    "patientEncounter",
                    "practitioner.same"
            );
        }

        PractitionerDTO newPractitioner = practitionerHelper.getPractitioner(newPractitionerId);

        // 1. Practitioner must be active
        if (!Boolean.TRUE.equals(newPractitioner.isActive())) {
            throw new BadRequestAlertException(
                    "Selected practitioner is not active",
                    "patientEncounter",
                    "practitioner.inactive"
            );
        }

        // 2. New practitioner must have same specialty
        if (oldPractitioner != null && !Objects.equals(oldPractitioner.specialty(), newPractitioner.specialty())) {
            throw new BadRequestAlertException(
                    "Selected practitioner must have the same specialty as the current practitioner",
                    "patientEncounter",
                    "practitioner.specialty.mismatch"
            );
        }

        // 3. New practitioner must have access to encounter department
        Long departmentId = encounter.getDepartmentId();

        if (departmentId == null) {
            throw new BadRequestAlertException(
                    "Encounter department is required",
                    "patientEncounter",
                    "department.required"
            );
        }

        boolean hasDepartmentAccess = practitionerClient.hasDepartmentAccess( newPractitionerId,departmentId);

        if (!hasDepartmentAccess) {
            throw new BadRequestAlertException(
                    "Selected practitioner does not have access to the encounter department",
                    "patientEncounter",
                    "practitioner.department.access.denied"
            );
        }

        // 4. Reassign
        encounter.setPractitionerId(newPractitioner.id());

        PatientEncounter saved = patientEncounterRepository.saveAndFlush(encounter);

        LOG.info(
                "[REASSIGN_PRACTITIONER] success encounterId={} oldPractitionerId={} newPractitionerId={} departmentId={} specialty={}",
                encounterId,
                oldPractitioner!=null?oldPractitioner.id():null,
                newPractitioner.id(),
                departmentId,
                newPractitioner.specialty()
        );

        return saved;
    }

    private void notifyEncounterEvent(PatientEncounter encounter, NotificationCode notificationCode, Map<String, Object> extraData) {
        if (encounter == null || notificationCode == null) {
            return;
        }
        try {
            DepartmentDTO department = encounter.getDepartmentId() != null ? departmentHelper.getDepartment(encounter.getDepartmentId()) : null;
            PractitionerDTO practitionerDTO = null;
            if (encounter.getPractitionerId() != null) {
                practitionerDTO = practitionerHelper.getPractitioner(encounter.getPractitionerId());
            }
            Map<String, Object> data = buildEncounterNotificationData(encounter, department);
            if (extraData != null && !extraData.isEmpty()) {
                data.putAll(extraData);
            }
            String login = SecurityUtils.getCurrentUserLogin().orElse(null);
            Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = notificationHelper.resolveRecipients(encounter.getDepartmentId(), login, encounter.getCompletedBy(), encounter.getPatient(), practitionerDTO, false);
            if (recipientsByRule.isEmpty()) {
                LOG.warn("[ENCOUNTER_NOTIFICATION] No recipients resolved. encounterId={}, code={}", encounter.getId(), notificationCode);
                return;
            }
            LOG.debug("[ENCOUNTER_NOTIFICATION] Creating notification. encounterId={}, code={}, recipientsByRule={}", encounter.getId(), notificationCode, recipientsByRule);
            notificationHelper.sendNotification(encounter.getFacilityId(), notificationCode, recipientsByRule, data, "PATIENT_ENCOUNTER", encounter.getId());
        } catch (Exception e) {
            LOG.warn("[ENCOUNTER_NOTIFICATION] Failed notification. encounterId={}, code={}, error={}", encounter.getId(), notificationCode, e.getMessage());
        }
    }

    private Map<String, Object> buildEncounterNotificationData(PatientEncounter encounter, DepartmentDTO department) {
        Map<String, Object> data = new LinkedHashMap<>();
        FacilityDTO facilityDTO = null;
        if (encounter.getFacilityId() != null) {
            facilityDTO = facilityHelper.getFacility(encounter.getFacilityId());
        }
        Patient patient = encounter.getPatient();
        data.put("facility_name", facilityDTO != null ? facilityDTO.name() : "");
        data.put("encounter_id", encounter.getId());
        data.put("patient_id", patient != null ? patient.getId() : null);
        data.put("patient_name", patient != null ? notificationHelper.getPatientName(patient) : "");
        data.put("patient_mrn", patient != null ? patient.getMedicalRecordNumber() : "");
        data.put("department_id", encounter.getDepartmentId());
        data.put("department_name", department != null ? department.name() : "");
        data.put("practitioner_id", encounter.getPractitionerId());
        data.put("encounter_type", encounter.getEncounterType() != null ? encounter.getEncounterType().name() : "");
        data.put("encounter_reason", encounter.getEncounterReason() != null ? encounter.getEncounterReason().name() : "");
        data.put("priority", encounter.getPriorityLevel() != null ? encounter.getPriorityLevel().name() : "");
        data.put("status", encounter.getStatus() != null ? encounter.getStatus().name() : "");
        data.put("encounter_number", encounter.getEncounterNumber());
        data.put("encounter_date", encounter.getEncounterDate() != null ? encounter.getEncounterDate().toString() : "");
        data.put("encounter_time", encounter.getEncounterTime() != null ? encounter.getEncounterTime().toString() : "");
        data.put("completed_at", encounter.getCompletedAt() != null ? encounter.getCompletedAt().toString() : "");
        data.put("completed_by", encounter.getCompletedBy() != null ? encounter.getCompletedBy() : "");
        data.put("chief_complaint", encounter.getChiefComplaint() != null ? encounter.getChiefComplaint() : "");
        data.put("notes", encounter.getNotes() != null ? encounter.getNotes() : "");
        return data;
    }
}