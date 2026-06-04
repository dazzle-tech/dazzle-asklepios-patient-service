package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.DiagnosticTestSetupDTO;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.AppointmentLog;
import com.dazzle.asklepios.domain.AppointmentReschedule;
import com.dazzle.asklepios.domain.AvailabilityGenerationBatch;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
import com.dazzle.asklepios.domain.enumeration.DayOfWeek;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.repository.AppointmentLogRepository;
import com.dazzle.asklepios.repository.AppointmentRescheduleRepository;
import com.dazzle.asklepios.repository.AvailabilityGenerationBatchRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.appointment.AppointmentBookPatientDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentCancelDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentNoShowDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentQuickAppointmentDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentRescheduleDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentSearchFilterDTO;
import com.dazzle.asklepios.service.dto.appointment.BulkAppointmentRescheduleDTO;
import com.dazzle.asklepios.service.dto.appointment.DiagnosticTestAppointmentRescheduleDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestCreateDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterCreateDTO;
import com.dazzle.asklepios.service.helper.CatalogHelper;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.DiagnosticTestHelper;
import com.dazzle.asklepios.service.helper.PractitionerHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.appointment.AppointmentQuickAppointmentResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointment.BulkAppointmentRescheduleResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointment.BulkReschedulePreviewVM;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentLogRepository appointmentLogRepository;

    private static final String ENTITY_NAME = "Appointment";

    private static final String SYSTEM_CANCEL_REASON = "cancel appointment from reschedule";
    private static final String SYSTEM_RESCHEDULE_REASON = "rescheduled due to availability change ";

    private static final Logger LOG = LoggerFactory.getLogger(AppointmentService.class);

    private final PatientRepository patientRepository;
    private final DepartmentHelper departmentHelper;
    private final PatientEncounterService patientEncounterService;
    private final PatientEncounterRepository patientEncounterRepository;
    private final AvailabilityGenerationBatchRepository availabilityGenerationBatchRepository;
    private final DiagnosticTestHelper diagnosticTestHelper;
    private final DiagnosticOrderService diagnosticOrderService;
    private final DiagnosticOrderTestService diagnosticOrderTestService;
    private final CatalogHelper catalogHelper;
    private final AppointmentRescheduleRepository appointmentRescheduleRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final PractitionerHelper practitionerHelper;

    public List<AppointmentLog> getAppointmentLogs(Long appointmentId) {
        LOG.debug("Request to get Appointment Log id={}", appointmentId);
        return appointmentLogRepository.findAllByAppointmentIdOrderByLogDateDesc(appointmentId);
    }

    public Appointment bookPatientAppointment(AppointmentBookPatientDTO dto) {
        LOG.debug("Request to update Appointment dto={}", dto);

        Appointment appointment = appointmentRepository.findById(dto.id())
                .orElseThrow(() -> new BadRequestAlertException("notfound", ENTITY_NAME, "Appointment not found with id: " + dto.id()));
        if (dto.patientId() != null) {
            Patient patient = patientRepository.findById(dto.patientId())
                    .orElseThrow(() -> new BadRequestAlertException("notfound", ENTITY_NAME, "Patient not found with id: " + dto.patientId()));
            appointment.setPatient(patient);
        }
        if (appointment.getRequirePractitioner() && dto.defaultPractitioner() == null) {
            throw new BadRequestAlertException("practitionerid", ENTITY_NAME, "Practitioner is required for this appointment");
        }
        if (dto.defaultService() != null) {
            appointment.setDefaultServiceId(dto.defaultService());
        }
        appointment.setDefaultPractitionerId(dto.defaultPractitioner());
        if (dto.reason() != null) {
            appointment.setReason(dto.reason());
        }
        if (dto.status() != null) {
            appointment.setStatus(dto.status());
        }
        if (dto.note() != null) {
            appointment.setNote(dto.note());
        }
        appointment.setService(dto.service());

        appointment.setPriority(dto.priority());

        if (dto.originType() != null) {
            appointment.setOriginType(dto.originType());
        }

        if (dto.originName() != null) {
            appointment.setOriginName(dto.originName());
        }

        if (dto.service() == EncounterReason.FOLLOW_UP && dto.followUpEncounterId() != null) {
            PatientEncounter followUpEncounter = patientEncounterRepository.findById(dto.followUpEncounterId())
                    .orElseThrow(() -> new BadRequestAlertException("notfound", ENTITY_NAME, "Patient Encounter not found with id: " + dto.followUpEncounterId()));

            appointment.setFollowUpEncounter(followUpEncounter);
        }
        return appointmentRepository.save(appointment);
    }

    public Page<Appointment> getAppointmentsByStatusBetweenDates(List<AppointmentStatus> status, Instant startDatetime, Instant endDatetime, Pageable pageable) {
        LOG.debug("Request to get appointments with patient not null between startDatetime={} and endDatetime={}", startDatetime, endDatetime);

        return appointmentRepository.findByStatusInAndStartDatetimeBetween(status, startDatetime, endDatetime, pageable);
    }

    public Page<Appointment> filterAppointment(AppointmentSearchFilterDTO filter, Pageable pageable) {

        LOG.debug("Service filter Appointments filter={} pageable={}", filter, pageable);

        if (filter.facility() == null) {
            throw new BadRequestAlertException("facility", ENTITY_NAME, "Facility is required");
        }

        Specification<Appointment> appointmentFilterSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            query.distinct(true);

            predicates.add(cb.equal(root.get("facilityId"), filter.facility()));

            if (filter.department() != null) {
                predicates.add(cb.equal(root.get("departmentId"), filter.department()));
            }

            if (filter.resourceType() != null) {
                predicates.add(cb.equal(root.get("resourceType"), filter.resourceType()));
            }

            if (filter.resourceId() != null) {
                predicates.add(cb.equal(root.get("resourceId"), filter.resourceId()));
            }

            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }

            if (filter.bookingMode() != null && !filter.bookingMode().isEmpty()) {
                predicates.add(root.get("bookingMode").in(filter.bookingMode()));
            } else {
                predicates.add(cb.notEqual(root.get("bookingMode"), BookingMode.BUFFER));
            }

            if (filter.patientId() != null) {
                predicates.add(cb.equal(root.join("patient", JoinType.LEFT).get("id"), filter.patientId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Appointment> result = appointmentRepository.findAll(appointmentFilterSpec, pageable);

        LOG.debug("[FILTER] Appointments result totalElements={} totalPages={} pageNumber={} pageSize={}",
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());

        return result;
    }
    public List<Appointment> getAppointmentsByStatusBetweenDatesWithoutPagination(
            List<AppointmentStatus> status,
            Instant startDatetime,
            Instant endDatetime
    ) {
        LOG.debug(
                "Request to get appointments by status={} between startDatetime={} and endDatetime={}",
                status,
                startDatetime,
                endDatetime
        );

        return appointmentRepository.findByStatusInAndStartDatetimeBetween(
                status,
                startDatetime,
                endDatetime
        );
    }

    public List<Appointment> filterAppointmentWithoutPagination(AppointmentSearchFilterDTO filter) {

        LOG.debug("Service filter Appointments filter={}", filter);

        if (filter.facility() == null) {
            throw new BadRequestAlertException("facility", ENTITY_NAME, "Facility is required");
        }

        Specification<Appointment> appointmentFilterSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            query.distinct(true);

            predicates.add(cb.equal(root.get("facilityId"), filter.facility()));

            if (filter.department() != null) {
                predicates.add(cb.equal(root.get("departmentId"), filter.department()));
            }

            if (filter.resourceType() != null) {
                predicates.add(cb.equal(root.get("resourceType"), filter.resourceType()));
            }

            if (filter.resourceId() != null) {
                predicates.add(cb.equal(root.get("resourceId"), filter.resourceId()));
            }

            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }

            if (filter.bookingMode() != null && !filter.bookingMode().isEmpty()) {
                predicates.add(root.get("bookingMode").in(filter.bookingMode()));
            } else {
                predicates.add(cb.notEqual(root.get("bookingMode"), BookingMode.BUFFER));
            }

            if (filter.patientId() != null) {
                predicates.add(cb.equal(root.join("patient", JoinType.LEFT).get("id"), filter.patientId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Appointment> result = appointmentRepository.findAll(appointmentFilterSpec);

        LOG.debug("[FILTER] Appointments result size={}", result.size());

        return result;
    }
    public Appointment cancel(AppointmentCancelDTO dto) {
        Appointment appointment = getAppointment(dto.id());

        validateCancelable(appointment);

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelReason(dto.cancelReason());
        appointment.setCancelledBy(currentUsername());
        return appointmentRepository.save(appointment);
    }

    public Appointment noShow(AppointmentNoShowDTO dto) {
        Appointment appointment = getAppointment(dto.id());

        validateNoShow(appointment);

        appointment.setStatus(AppointmentStatus.NO_SHOW);
        appointment.setNoShowReason(dto.noShowReason());
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment confirm(Long id) {
        Appointment appointment = getAppointment(id);

        validateConfirmable(appointment);

        if (appointment.getPatient() == null) {
            throw new BadRequestAlertException("patientrequired", ENTITY_NAME, "Cannot confirm appointment without patient");
        }
        appointment.setConfirmedAt(Instant.now());
        appointment.setStatus(AppointmentStatus.CONFIRMED);

        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment checkIn(Long id) {
        Appointment appointment = getAppointment(id);

        validateCheckIn(appointment);

        if (appointment.getPatient() == null) {
            throw new BadRequestAlertException("patientrequired", ENTITY_NAME, "Cannot check in appointment without patient");
        }
        if (!appointment.getPatient().getIsCompletedPatient()) {
            throw new BadRequestAlertException("notcompletedpatient", ENTITY_NAME, "Cannot check in appointment for not completed patient");
        }

        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        appointment.setCheckedInAt(Instant.now());

        Appointment savedAppointment = appointmentRepository.save(appointment);

        DepartmentDTO department = departmentHelper.getDepartment(savedAppointment.getDepartmentId());

        PatientEncounter encounter = createEncounter(savedAppointment, department);

        if (savedAppointment.getResourceType() == TemplateType.DIAGNOSTIC_TEST) {
            encounter = patientEncounterService.startEncounter(encounter.getId());
            createAndSubmitDiagnosticOrderFlow(savedAppointment, encounter);
        } else if (savedAppointment.getResourceType() == TemplateType.CATALOG) {
            encounter = patientEncounterService.startEncounter(encounter.getId());
            createAndSubmitCatalogOrderFlow(savedAppointment, encounter);
        }

        return savedAppointment;
    }

    public AppointmentQuickAppointmentResponseVM createQuickAppointment(AppointmentQuickAppointmentDTO appointmentDTO) {
        LOG.info("[CREATE QUICK APPOINTMENT] facilityId={}, departmentId={}, resourceType={}, resourceId={}, patientId={}",
                appointmentDTO.facilityId(),
                appointmentDTO.departmentId(),
                appointmentDTO.resourceType(),
                appointmentDTO.resourceId(),
                appointmentDTO.patientId());

        DepartmentDTO department = departmentHelper.getDepartment(appointmentDTO.departmentId());
        if (appointmentDTO.resourceType() == TemplateType.DEPARTMENT) {
            validateDepartmentWorkingDay(department);
        } else if (appointmentDTO.resourceType() == TemplateType.PRACTITIONER) {
            PractitionerDTO practitioner = practitionerHelper.getPractitioner(appointmentDTO.resourceId());
            validatePractitionerWorkingDay(practitioner);
        }

        if (department.defaultDurationMinutes() == null || department.defaultDurationMinutes() <= 0) {
            throw new BadRequestAlertException(
                    "defaultdurationinvalid",
                    "department",
                    "Department default duration is invalid"
            );
        }

        Patient patient = patientRepository.findById(appointmentDTO.patientId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "Patient not found",
                        "patient",
                        "idnotfound"
                ));


        Instant startDateTime = Instant.now();
        Instant endDateTime = startDateTime.plusSeconds(department.defaultDurationMinutes() * 60L);

        Appointment appointment = new Appointment();
        appointment.setFacilityId(appointmentDTO.facilityId());
        appointment.setDepartmentId(appointmentDTO.departmentId());
        appointment.setResourceType(appointmentDTO.resourceType());
        appointment.setResourceId(appointmentDTO.resourceId());
        appointment.setPatient(patient);
        appointment.setStartDatetime(startDateTime);
        appointment.setEndDatetime(endDateTime);
        appointment.setDefaultServiceId(appointmentDTO.defaultServiceId());
        appointment.setDefaultPractitionerId(appointmentDTO.defaultPractitionerId());
        appointment.setBookingMode(BookingMode.QUICK);
        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        appointment.setPriority(appointmentDTO.priority());
        appointment.setOriginType(appointmentDTO.originType());
        appointment.setOriginName(appointmentDTO.originName());
        appointment.setRequireConfirmation(true);
        appointment.setReason(appointmentDTO.reason());
        appointment.setNote(appointmentDTO.note());
        appointment.setService(appointmentDTO.service());
        if (appointmentDTO.service() == EncounterReason.FOLLOW_UP && appointmentDTO.followUpEncounterId() != null) {
            PatientEncounter followUpEncounter = patientEncounterRepository.findById(appointmentDTO.followUpEncounterId())
                    .orElseThrow(() -> new BadRequestAlertException("Patient Encounter not found with id: " + appointmentDTO.followUpEncounterId(), ENTITY_NAME, "notfound"));

            appointment.setFollowUpEncounter(followUpEncounter);
        }
        appointment.setCapacityIndex(1);
        Appointment quickAppointment = appointmentRepository.save(appointment);
        PatientEncounter encounter = createEncounter(quickAppointment, department);

        return new AppointmentQuickAppointmentResponseVM(quickAppointment, encounter);
    }

    public Page<Appointment> getAppointmentByAvailabilityGenerationBatch(Long availabilityGenerationId, Pageable pageable) {
        LOG.debug("Request to get appointments for availability generation batch availabilityGenerationBatchId={}", availabilityGenerationId);

        AvailabilityGenerationBatch batch = getBatch(availabilityGenerationId);

        return appointmentRepository.findByAvailabilityGenerationBatch_Id(batch.getId(), pageable);
    }

    public Page<Appointment> getAppointmentsByDepartmentBetweenDates(Long departmentId, Instant startDatetime, Instant endDatetime, Pageable pageable) {
        LOG.debug("Request to get appointments for the department between startDatetime={} and endDatetime={}", startDatetime, endDatetime);


        return appointmentRepository.findByDepartmentIdAndStartDatetimeBetween(departmentId, startDatetime, endDatetime, pageable);
    }

    public Appointment getById(Long appointmentId) {
        LOG.debug("[GET_BY_ID] appointmentId={}", appointmentId);

        return appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> {
                    LOG.warn("[GET_BY_ID] appointment not found id={}", appointmentId);
                    return new BadRequestAlertException(
                            "appointment not found with id " + appointmentId,
                            "appointment",
                            "id.notfound"
                    );
                });
    }

    @Transactional
    public Appointment reschedule(AppointmentRescheduleDTO dto) {
        Appointment oldAppointment = getAppointment(dto.oldAppointmentId());
        Appointment newAppointment = getAppointment(dto.newAppointmentId());

        validateReschedule(oldAppointment, false);
        validateFreeSlotForReschedule(oldAppointment, newAppointment);

        return executeSingleReschedule(oldAppointment, newAppointment, dto.rescheduleReason());
    }

    @Transactional
    public Appointment rescheduleDiagnosticTestAppointment(DiagnosticTestAppointmentRescheduleDTO dto) {
        LOG.debug("[RESCHEDULE_DIAGNOSTIC_TEST_APPOINTMENT] orderTestId={}, newAppointmentId={}", dto.orderTestId(), dto.newAppointmentId());

        DiagnosticOrderTest orderTest = diagnosticOrderTestRepository.findById(dto.orderTestId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Diagnostic order test not found with id: " + dto.orderTestId(),
                        "DiagnosticOrderTest",
                        "notfound"
                ));

        if (orderTest.getOrderId() == null) {
            throw new BadRequestAlertException(
                    "Diagnostic order test is not linked to an order",
                    "DiagnosticOrderTest",
                    "orderrequired"
            );
        }
        DiagnosticOrder order = diagnosticOrderRepository.findById(orderTest.getOrderId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Diagnostic order  not found with id: " + orderTest.getOrderId(),
                        "DiagnosticOrder",
                        "notfound"
                ));

        if (order.getEncounter() == null) {
            throw new BadRequestAlertException(
                    "Diagnostic order is not linked to an encounter",
                    "DiagnosticOrder",
                    "encounterrequired"
            );
        }

        PatientEncounter encounter = order.getEncounter();

        if (encounter.getAppointment() == null) {
            throw new BadRequestAlertException(
                    "Encounter is not linked to an appointment",
                    "DiagnosticOrderTest",
                    "appointmentrequired"
            );
        }

        if (orderTest.getTestId() == null) {
            throw new BadRequestAlertException(
                    "Diagnostic order test does not have diagnostic test id",
                    "DiagnosticOrderTest",
                    "testidrequired"
            );
        }

        Appointment oldAppointment = getAppointment(encounter.getAppointment().getId());
        LOG.debug("[RESCHEDULE_DIAGNOSTIC_TEST_APPOINTMENT] oldAppointment loaded id={}, status={}", oldAppointment.getId(), oldAppointment.getStatus());
        Appointment newAppointment = getAppointment(dto.newAppointmentId());

        validateReschedule(oldAppointment, true);

        validateDiagnosticTestFreeSlotForReschedule(oldAppointment, newAppointment, orderTest.getTestId());

        Appointment savedNewAppointment = executeSingleReschedule(oldAppointment, newAppointment, dto.rescheduleReason());

        orderTest.setStatus(DiagnosticOrderTestStatus.RESCHEDULED);
        diagnosticOrderTestRepository.save(orderTest);

        return savedNewAppointment;
    }

    @Transactional(readOnly = true)
    public BulkReschedulePreviewVM getBulkReschedulePreview(
            Long availabilityGenerationBatchId,
            boolean includeFreeSlots,
            Long departmentId,
            String templateName
    ) {
        LOG.debug(
                "[BULK_RESCHEDULE_PREVIEW] batchId={} includingFreeSlot={} departmentId={} templateName={}",
                availabilityGenerationBatchId,
                includeFreeSlots,
                departmentId,
                templateName
        );

        getBatch(availabilityGenerationBatchId);

        Instant tomorrowStart = tomorrowStartInstant();

        List<AppointmentStatus> statuses = includeFreeSlots
                ? List.of(AppointmentStatus.NEW, AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED)
                : List.of(AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED);

        boolean hasDepartment = departmentId != null;
        boolean hasTemplateName = templateName != null && !templateName.trim().isEmpty();

        List<Appointment> appointments;

        if (hasDepartment && hasTemplateName) {
            appointments =
                    appointmentRepository
                            .findByAvailabilityGenerationBatch_IdAndStatusInAndStartDatetimeGreaterThanAndDepartmentIdAndAvailabilityGenerationBatch_Template_TemplateNameContainingIgnoreCaseOrderByStartDatetimeAsc(
                                    availabilityGenerationBatchId,
                                    statuses,
                                    tomorrowStart,
                                    departmentId,
                                    templateName.trim()
                            );
        } else if (hasDepartment) {
            appointments =
                    appointmentRepository
                            .findByAvailabilityGenerationBatch_IdAndStatusInAndStartDatetimeGreaterThanAndDepartmentIdOrderByStartDatetimeAsc(
                                    availabilityGenerationBatchId,
                                    statuses,
                                    tomorrowStart,
                                    departmentId
                            );
        } else if (hasTemplateName) {
            appointments =
                    appointmentRepository
                            .findByAvailabilityGenerationBatch_IdAndStatusInAndStartDatetimeGreaterThanAndAvailabilityGenerationBatch_Template_TemplateNameContainingIgnoreCaseOrderByStartDatetimeAsc(
                                    availabilityGenerationBatchId,
                                    statuses,
                                    tomorrowStart,
                                    templateName.trim()
                            );
        } else {
            appointments =
                    appointmentRepository
                            .findByAvailabilityGenerationBatch_IdAndStatusInAndStartDatetimeGreaterThanOrderByStartDatetimeAsc(
                                    availabilityGenerationBatchId,
                                    statuses,
                                    tomorrowStart
                            );
        }

        return new BulkReschedulePreviewVM(appointments);
    }

    @Transactional
    public void cancelBulkRescheduleAppointments(Long availabilityGenerationBatchId) {
        LOG.debug("[BULK_RESCHEDULE_CANCEL] batchId={}", availabilityGenerationBatchId);

        getBatch(availabilityGenerationBatchId);

        Instant tomorrowStart = tomorrowStartInstant();

        List<Appointment> appointmentsToCancel =
                appointmentRepository
                        .findByAvailabilityGenerationBatch_IdAndStatusInAndStartDatetimeGreaterThanOrderByStartDatetimeAsc(
                                availabilityGenerationBatchId,
                                List.of(
                                        AppointmentStatus.NEW,
                                        AppointmentStatus.BOOKED,
                                        AppointmentStatus.CONFIRMED
                                ),
                                tomorrowStart
                        );

        String username = currentUsername();

        for (Appointment appointment : appointmentsToCancel) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointment.setCancelReason(SYSTEM_CANCEL_REASON);
            appointment.setCancelledBy(username);

            // TODO: trigger patient notification here for booked/confirmed appointments.

        }

        appointmentRepository.saveAll(appointmentsToCancel);

        LOG.info("[BULK_RESCHEDULE_CANCEL] cancelledCount={}", appointmentsToCancel.size());
    }

    @Transactional
    public BulkAppointmentRescheduleResponseVM bulkReschedule(BulkAppointmentRescheduleDTO dto) {
        LOG.debug(
                "[BULK_RESCHEDULE] originalBatchId={}, replacementBatchId={}",
                dto.originalAvailabilityGenerationBatchId(),
                dto.replacementAvailabilityGenerationBatchId()
        );

        AvailabilityGenerationBatch originalBatch = getBatch(dto.originalAvailabilityGenerationBatchId());
        AvailabilityGenerationBatch replacementBatch = getBatch(dto.replacementAvailabilityGenerationBatchId());

        if (originalBatch.getId().equals(replacementBatch.getId())) {
            throw new BadRequestAlertException(
                    "Original and replacement generation batches cannot be the same",
                    ENTITY_NAME,
                    "samebatch"
            );
        }

        Instant tomorrowStart = tomorrowStartInstant();

        List<Appointment> oldAppointments =
                appointmentRepository.findByAvailabilityGenerationBatch_IdAndStatusInAndStartDatetimeGreaterThanOrderByStartDatetimeAsc(originalBatch.getId(), List.of(AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED), tomorrowStart);

        List<Appointment> newAvailableAppointments = appointmentRepository.findByAvailabilityGenerationBatch_IdAndStatusAndStartDatetimeGreaterThanAndBookingModeInOrderByStartDatetimeAsc(replacementBatch.getId(), AppointmentStatus.NEW, tomorrowStart, List.of(BookingMode.SLOT));

        if (oldAppointments.size() > newAvailableAppointments.size()) {
            List<Long> unmatchedOldAppointmentIds = oldAppointments
                    .subList(newAvailableAppointments.size(), oldAppointments.size())
                    .stream()
                    .map(Appointment::getId)
                    .toList();

            LOG.warn(
                    "[BULK_RESCHEDULE] insufficient replacement slots oldCount={}, newAvailableCount={}, unmatchedCount={}",
                    oldAppointments.size(),
                    newAvailableAppointments.size(),
                    unmatchedOldAppointmentIds.size()
            );

            return new BulkAppointmentRescheduleResponseVM(false, "Not enough available appointments in the selected replacement batch", unmatchedOldAppointmentIds);
        }

        for (int i = 0; i < oldAppointments.size(); i++) {
            Appointment oldAppointment = oldAppointments.get(i);
            Appointment newAppointment = newAvailableAppointments.get(i);

            validateReschedule(oldAppointment, false);
            validateFreeSlotForBulkReschedule(oldAppointment, newAppointment);

            executeSingleReschedule(oldAppointment, newAppointment, SYSTEM_RESCHEDULE_REASON);
        }
        cancelFutureFreeAppointmentsFromBatch(originalBatch.getId(), tomorrowStart);

        return new BulkAppointmentRescheduleResponseVM(true, "Reschedule applied successfully", List.of());
    }

    private Instant tomorrowStartInstant() {
        return LocalDate.now(ZoneId.systemDefault())
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
    }

    private void cancelFutureFreeAppointmentsFromBatch(Long availabilityGenerationBatchId, Instant tomorrowStart) {
        List<Appointment> freeAppointments =
                appointmentRepository
                        .findByAvailabilityGenerationBatch_IdAndStatusAndStartDatetimeGreaterThanAndBookingModeInOrderByStartDatetimeAsc(
                                availabilityGenerationBatchId,
                                AppointmentStatus.NEW,
                                tomorrowStart,
                                List.of(BookingMode.SLOT, BookingMode.BUFFER)
                        );

        String username = currentUsername();

        for (Appointment appointment : freeAppointments) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointment.setCancelReason(SYSTEM_CANCEL_REASON);
            appointment.setCancelledBy(username);
        }

        appointmentRepository.saveAll(freeAppointments);

        LOG.info("[BULK_RESCHEDULE] cancelledFreeAppointmentsCount={}", freeAppointments.size());
    }

    private void validateDiagnosticTestFreeSlotForReschedule(Appointment oldAppointment, Appointment newAppointment, Long diagnosticTestId) {
        if (newAppointment.getStatus() != AppointmentStatus.NEW) {
            throw new BadRequestAlertException(
                    "Selected appointment must be free",
                    ENTITY_NAME,
                    "slotnotfree"
            );
        }

        if (newAppointment.getBookingMode() != BookingMode.SLOT) {
            throw new BadRequestAlertException(
                    "Selected appointment must be a slot appointment",
                    ENTITY_NAME,
                    "invalidslotbookingmode"
            );
        }

        if (newAppointment.getResourceType() != TemplateType.DIAGNOSTIC_TEST) {
            throw new BadRequestAlertException(
                    "Selected appointment must be for diagnostic test resource type",
                    ENTITY_NAME,
                    "invalidresourcetype"
            );
        }

        if (!equalsNullable(diagnosticTestId, newAppointment.getResourceId())) {
            throw new BadRequestAlertException(
                    "Selected appointment must be for the same diagnostic test",
                    ENTITY_NAME,
                    "diagnostictestmismatch"
            );
        }

        if (!equalsNullable(oldAppointment.getFacilityId(), newAppointment.getFacilityId())) {
            throw new BadRequestAlertException(
                    "Selected appointment must belong to the same facility",
                    ENTITY_NAME,
                    "facilitymismatch"
            );
        }
    }

    private Appointment executeSingleReschedule(Appointment oldAppointment, Appointment newAppointment, String rescheduleReason) {
        if (oldAppointment.getId().equals(newAppointment.getId())) {
            throw new BadRequestAlertException(
                    "Old appointment and new appointment cannot be the same",
                    ENTITY_NAME,
                    "sameappointment"
            );
        }

        Instant newStartDatetime = newAppointment.getStartDatetime();
        Instant newEndDatetime = newAppointment.getEndDatetime();

        if (newStartDatetime == null || newEndDatetime == null) {
            throw new BadRequestAlertException(
                    "Selected appointment slot must have start and end datetime",
                    ENTITY_NAME,
                    "slotdatetimerequired"
            );
        }
        copyAppointmentDataForReschedule(oldAppointment, newAppointment);

        oldAppointment.setStatus(AppointmentStatus.RESCHEDULED);
        oldAppointment.setPatient(null);
        oldAppointment.setReason(null);
        oldAppointment.setNote(null);
        oldAppointment.setService(null);
        oldAppointment.setPriority(EncounterPriority.NORMAL);
        oldAppointment.setOriginName(null);
        oldAppointment.setOriginType(null);
        oldAppointment.setFollowUpEncounter(null);


        newAppointment.setStartDatetime(newStartDatetime);
        newAppointment.setEndDatetime(newEndDatetime);
        newAppointment.setStatus(AppointmentStatus.BOOKED);

        Appointment savedOldAppointment = appointmentRepository.save(oldAppointment);
        Appointment savedNewAppointment = appointmentRepository.save(newAppointment);

        AppointmentReschedule appointmentReschedule = new AppointmentReschedule();
        appointmentReschedule.setOldAppointmentId(savedOldAppointment.getId());
        appointmentReschedule.setNewAppointmentId(savedNewAppointment.getId());
        appointmentReschedule.setRescheduleReason(rescheduleReason);
        appointmentReschedule.setCreatedBy(currentUsername());

        appointmentRescheduleRepository.save(appointmentReschedule);

        return savedNewAppointment;
    }

    private void createAndSubmitDiagnosticOrderFlow(Appointment appointment, PatientEncounter encounter) {
        if (appointment.getResourceId() == null) {
            throw new BadRequestAlertException("resourceidrequired", ENTITY_NAME, "Diagnostic test appointment must have resourceId");
        }

        if (appointment.getPatient() == null || appointment.getPatient().getId() == null) {
            throw new BadRequestAlertException("patientrequired", ENTITY_NAME, "Diagnostic test appointment must have patient");
        }

        DiagnosticTestSetupDTO diagnosticTest = diagnosticTestHelper.getDiagnosticTest(appointment.getResourceId());

        if (diagnosticTest == null) {
            throw new BadRequestAlertException("Diagnostic test not found with id: " + appointment.getResourceId(), "DiagnosticTest", "notfound");
        }

        if (Boolean.FALSE.equals(diagnosticTest.isActive())) {
            throw new BadRequestAlertException("inactive", "DiagnosticTest", "Diagnostic test is inactive");
        }

        DiagnosticOrderCreateDTO orderCreateDTO = new DiagnosticOrderCreateDTO(
                appointment.getPatient().getId(),
                encounter.getId(),
                false,
                resolveLabStatus(diagnosticTest.type()),
                resolveRadStatus(diagnosticTest.type()),
                appointment.getDepartmentId(),
                appointment.getFacilityId()
        );

        DiagnosticOrder diagnosticOrder = diagnosticOrderService.create(orderCreateDTO);

        DiagnosticOrderTestCreateDTO orderTestCreateDTO = new DiagnosticOrderTestCreateDTO(
                diagnosticOrder.getId(),
                diagnosticTest.id(),
                appointment.getDepartmentId(),
                appointment.getReason(),
                appointment.getNote(),
                diagnosticTest.type(),
                null
        );

        diagnosticOrderTestService.create(orderTestCreateDTO);

        diagnosticOrderService.submit(diagnosticOrder, currentUsername());
    }

    private void createAndSubmitCatalogOrderFlow(Appointment appointment, PatientEncounter encounter) {
        if (appointment.getResourceId() == null) {
            throw new BadRequestAlertException("resourceidrequired", ENTITY_NAME, "Catalog appointment must have resourceId");
        }

        if (appointment.getPatient() == null || appointment.getPatient().getId() == null) {
            throw new BadRequestAlertException("patientrequired", ENTITY_NAME, "Catalog appointment must have patient");
        }

        List<DiagnosticTestSetupDTO> diagnosticTests = catalogHelper.getTestsByCatalog(appointment.getResourceId());

        if (diagnosticTests == null || diagnosticTests.isEmpty()) {
            throw new BadRequestAlertException("empty", "Catalog", "Catalog does not contain diagnostic tests");
        }

        boolean hasLab = diagnosticTests.stream()
                .anyMatch(test -> test.type() == TestType.LABORATORY);

        boolean hasRadiology = diagnosticTests.stream()
                .anyMatch(test -> test.type() == TestType.RADIOLOGY);

        DiagnosticOrderCreateDTO orderCreateDTO = new DiagnosticOrderCreateDTO(
                appointment.getPatient().getId(),
                encounter.getId(),
                false,
                hasLab ? DiagnosticStatus.NEW : null,
                hasRadiology ? DiagnosticStatus.NEW : null,
                appointment.getDepartmentId(),
                appointment.getFacilityId()
        );

        DiagnosticOrder diagnosticOrder = diagnosticOrderService.create(orderCreateDTO);

        for (DiagnosticTestSetupDTO diagnosticTest : diagnosticTests) {
            if (diagnosticTest == null) {
                continue;
            }

            if (Boolean.FALSE.equals(diagnosticTest.isActive())) {
                throw new BadRequestAlertException(
                        "inactive",
                        "DiagnosticTest",
                        "Diagnostic test is inactive: " + diagnosticTest.id()
                );
            }

            DiagnosticOrderTestCreateDTO orderTestCreateDTO = new DiagnosticOrderTestCreateDTO(
                    diagnosticOrder.getId(),
                    diagnosticTest.id(),
                    appointment.getDepartmentId(),
                    appointment.getReason(),
                    appointment.getNote(),
                    diagnosticTest.type(),
                    null
            );

            diagnosticOrderTestService.create(orderTestCreateDTO);
        }

        diagnosticOrderService.submit(diagnosticOrder, currentUsername());
    }

    private DiagnosticStatus resolveLabStatus(TestType type) {
        if (type == null) {
            return DiagnosticStatus.NEW;
        }

        return switch (type) {
            case LABORATORY, PATHOLOGY, MICROBIOLOGY -> DiagnosticStatus.NEW;
            case RADIOLOGY -> null;
        };
    }

    private DiagnosticStatus resolveRadStatus(TestType type) {
        if (type == null) {
            return DiagnosticStatus.NEW;
        }

        return switch (type) {
            case RADIOLOGY -> DiagnosticStatus.NEW;
            case LABORATORY, PATHOLOGY, MICROBIOLOGY -> null;
        };
    }

    private AvailabilityGenerationBatch getBatch(Long id) {
        return availabilityGenerationBatchRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException("Batch not found: " + id, ENTITY_NAME, "notfound"));
    }

    private PatientEncounter createEncounter(Appointment savedAppointment, DepartmentDTO department) {

        PatientEncounterCreateDTO encounterCreateDTO =
                new PatientEncounterCreateDTO(
                        savedAppointment.getPatient() != null
                                ? savedAppointment.getPatient().getId()
                                : null,

                        savedAppointment.getFacilityId(),

                        savedAppointment.getDepartmentId(),

                        savedAppointment.getDefaultPractitionerId(),

                        savedAppointment.getId(),

                        department.encounterType(),

                        savedAppointment.getService(),

                        savedAppointment.getFollowUpEncounter() != null
                                ? savedAppointment.getFollowUpEncounter().getId()
                                : null,

                        savedAppointment.getPriority(),

                        savedAppointment.getOriginType(),

                        savedAppointment.getOriginName(),

                        savedAppointment.getNote(),

                        savedAppointment.getStartDatetime()
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate(),

                        savedAppointment.getStartDatetime()
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalTime(),

                        EncounterStatus.NEW,

                        savedAppointment.getReason()

                );

        return patientEncounterService.create(encounterCreateDTO);
    }

    private Appointment getAppointment(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException("Appointment not found: " + id, ENTITY_NAME, "notfound"));
    }

    private void validateCancelable(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BadRequestAlertException("alreadycancelled", ENTITY_NAME, "Appointment already cancelled");
        } else if (appointment.getStatus() == AppointmentStatus.CHECKED_IN) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Checked-in appointment cannot be cancelled");
        } else if (appointment.getStatus() == AppointmentStatus.IN_SERVICE) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "IN_SERVICE appointment cannot be cancelled");
        } else if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Completed appointment cannot be cancelled");
        }

    }

    private void validateNoShow(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Cancelled appointment cannot be marked as no-show");
        }
        if (appointment.getStatus() == AppointmentStatus.CHECKED_IN) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Checked-in appointment cannot be marked as no-show");
        } else if (appointment.getStatus() == AppointmentStatus.IN_SERVICE) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "IN_SERVICE appointment cannot be No-show");
        } else if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Completed appointment cannot be No-show");
        }
    }

    private void validateConfirmable(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Only booked appointments can be confirmed");
        }
    }

    private void validateCheckIn(Appointment appointment) {
        boolean requireConfirmation = !Boolean.FALSE.equals(appointment.getRequireConfirmation());
        boolean eligibleStatus =
                appointment.getStatus() == AppointmentStatus.BOOKED ||
                        appointment.getStatus() == AppointmentStatus.CONFIRMED;

        if (!eligibleStatus) {
            throw new BadRequestAlertException(
                    "invalidstatus",
                    ENTITY_NAME,
                    "Only booked or confirmed appointments can be checked in"
            );
        }


        if (requireConfirmation && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Only confirmed appointments can be checked in");
        }
        if (appointment.getStartDatetime() == null) {
            throw new BadRequestAlertException(
                    "Appointment start date is missing",
                    ENTITY_NAME,
                    "startdatetimerequired"


            );
        }

        LocalDate appointmentDate = appointment.getStartDatetime()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        if (!today.equals(appointmentDate)) {
            throw new BadRequestAlertException(
                    "invalidcheckindate",
                    ENTITY_NAME,
                    "Check-in is allowed only on the appointment date"
            );
        }
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException("No authenticated user", ENTITY_NAME, "unauthenticated"));
    }

    //Reschedule Helper

    private void validateReschedule(Appointment appointment, boolean allowReschedulingOfInServiceAppointments) {
        if (!allowReschedulingOfInServiceAppointments && appointment.getStatus() != AppointmentStatus.BOOKED && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BadRequestAlertException(
                    "Only booked or confirmed appointments can be rescheduled",
                    ENTITY_NAME,
                    "invalidstatus"
            );
        } else if (allowReschedulingOfInServiceAppointments && appointment.getStatus() != AppointmentStatus.IN_SERVICE) {
            throw new BadRequestAlertException(
                    "Only in-service appointments can be rescheduled",
                    ENTITY_NAME,
                    "invalidstatus"
            );
        }

        if (appointment.getPatient() == null || appointment.getPatient().getId() == null) {
            throw new BadRequestAlertException(
                    "Cannot reschedule appointment without patient",
                    ENTITY_NAME,
                    "patientrequired"
            );
        }
    }

    private void validateFreeSlotForReschedule(Appointment oldAppointment, Appointment newAppointment) {
        if (newAppointment.getStatus() != AppointmentStatus.NEW) {
            throw new BadRequestAlertException(
                    "Selected appointment must be free",
                    ENTITY_NAME,
                    "slotnotfree"
            );
        }

        if (newAppointment.getBookingMode() != BookingMode.SLOT) {
            throw new BadRequestAlertException(
                    "Selected appointment must be a slot appointment",
                    ENTITY_NAME,
                    "invalidslotbookingmode"
            );
        }

        if (!equalsNullable(oldAppointment.getFacilityId(), newAppointment.getFacilityId())) {
            throw new BadRequestAlertException(
                    "Selected appointment must belong to the same facility",
                    ENTITY_NAME,
                    "facilitymismatch"
            );
        }

        if (!equalsNullable(oldAppointment.getDepartmentId(), newAppointment.getDepartmentId())) {
            throw new BadRequestAlertException(
                    "Selected appointment must belong to the same department",
                    ENTITY_NAME,
                    "departmentmismatch"
            );
        }

        if (oldAppointment.getResourceType() != newAppointment.getResourceType()) {
            throw new BadRequestAlertException(
                    "Selected appointment must have the same resource type",
                    ENTITY_NAME,
                    "resourcetypemismatch"
            );
        }

        if (!equalsNullable(oldAppointment.getResourceId(), newAppointment.getResourceId())) {
            throw new BadRequestAlertException(
                    "Selected appointment must have the same resource",
                    ENTITY_NAME,
                    "resourcemismatch"
            );
        }

    }

    private void validateFreeSlotForBulkReschedule(Appointment oldAppointment, Appointment newAppointment) {
        if (newAppointment.getStatus() != AppointmentStatus.NEW) {
            throw new BadRequestAlertException(
                    "Selected appointment must be free",
                    ENTITY_NAME,
                    "slotnotfree"
            );
        }

        if (newAppointment.getBookingMode() != BookingMode.SLOT) {
            throw new BadRequestAlertException(
                    "Selected appointment must be a slot appointment",
                    ENTITY_NAME,
                    "invalidslotbookingmode"
            );
        }

        if (!equalsNullable(oldAppointment.getFacilityId(), newAppointment.getFacilityId())) {
            throw new BadRequestAlertException(
                    "Selected appointment must belong to the same facility",
                    ENTITY_NAME,
                    "facilitymismatch"
            );
        }

        if (!equalsNullable(oldAppointment.getDepartmentId(), newAppointment.getDepartmentId())) {
            throw new BadRequestAlertException(
                    "Selected appointment must belong to the same department",
                    ENTITY_NAME,
                    "departmentmismatch"
            );
        }
    }

    private boolean equalsNullable(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private void copyAppointmentDataForReschedule(Appointment oldAppointment, Appointment newAppointment) {
        newAppointment.setPatient(oldAppointment.getPatient());

        newAppointment.setFacilityId(oldAppointment.getFacilityId());
        newAppointment.setDepartmentId(oldAppointment.getDepartmentId());
        newAppointment.setResourceType(oldAppointment.getResourceType());
        newAppointment.setResourceId(oldAppointment.getResourceId());

        newAppointment.setDefaultServiceId(oldAppointment.getDefaultServiceId());
        newAppointment.setDefaultPractitionerId(oldAppointment.getDefaultPractitionerId());

        newAppointment.setBookingMode(oldAppointment.getBookingMode());
        newAppointment.setPriority(oldAppointment.getPriority());
        newAppointment.setOriginType(oldAppointment.getOriginType());
        newAppointment.setOriginName(oldAppointment.getOriginName());
        newAppointment.setReason(oldAppointment.getReason());
        newAppointment.setNote(oldAppointment.getNote());
        newAppointment.setService(oldAppointment.getService());
        newAppointment.setFollowUpEncounter(oldAppointment.getFollowUpEncounter());


        newAppointment.setCancelReason(null);
        newAppointment.setCancelledBy(null);
        newAppointment.setNoShowReason(null);
        newAppointment.setConfirmedAt(null);
        newAppointment.setCheckedInAt(null);
    }

    // Quick appointment helper
    private void validateDepartmentWorkingDay(DepartmentDTO department) {
        if (department.workingDays() == null || department.workingDays().isEmpty()) {
            throw new BadRequestAlertException(
                    "departmentworkingdaysnotconfigured",
                    "department",
                    "Department working days are not configured"
            );
        }

        DayOfWeek today = DayOfWeek.valueOf(LocalDate.now().getDayOfWeek().name());

        boolean isWorkingDay = department.workingDays().stream()
                .anyMatch(workingDay -> workingDay.getDayOfWeek() == today && Boolean.TRUE.equals(workingDay.getIsWorking()));

        if (!isWorkingDay) {
            throw new BadRequestAlertException(
                    "departmentnotworkingtoday",
                    "department",
                    "Department is not working today"
            );
        }
    }

    private void validatePractitionerWorkingDay(PractitionerDTO practitionerDTO) {
        if (practitionerDTO.workingDays() == null || practitionerDTO.workingDays().isEmpty()) {
            throw new BadRequestAlertException(
                    "practitionerworkingdaysnotconfigured",
                    "practitioner",
                    "practitioner working days are not configured"
            );
        }

        DayOfWeek today = DayOfWeek.valueOf(LocalDate.now().getDayOfWeek().name());

        boolean isWorkingDay = practitionerDTO.workingDays().stream()
                .anyMatch(workingDay -> workingDay.getDayOfWeek() == today && Boolean.TRUE.equals(workingDay.getIsWorking()));

        if (!isWorkingDay) {
            throw new BadRequestAlertException(
                    "practitionernotworkingtoday",
                    "practitioner",
                    "Practitioner is not working today"

            );
        }
    }

}
