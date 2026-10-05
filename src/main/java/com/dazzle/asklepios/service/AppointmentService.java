package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.dto.CatalogDTO;
import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.DiagnosticTestSetupDTO;
import com.dazzle.asklepios.client.setup.dto.FacilityDTO;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.client.setup.dto.RoomDTO;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.Appointment;
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
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import com.dazzle.asklepios.repository.AppointmentLogRepository;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.repository.AppointmentRescheduleRepository;
import com.dazzle.asklepios.repository.AvailabilityGenerationBatchRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.appointment.AppointmentBookPatientDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentCancelDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentIntegrationCreateDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentNoShowDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentQuickAppointmentDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentRescheduleDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentSearchFilterMultiDepartmentDTO;
import com.dazzle.asklepios.service.dto.appointment.AppointmentTransferMappingDTO;
import com.dazzle.asklepios.service.dto.appointment.BulkAppointmentRescheduleDTO;
import com.dazzle.asklepios.service.dto.appointment.BulkAppointmentTransferDTO;
import com.dazzle.asklepios.service.dto.appointment.DiagnosticTestAppointmentRescheduleDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestCreateDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterCreateDTO;
import com.dazzle.asklepios.service.helper.CatalogHelper;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.DiagnosticTestHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.service.helper.NotificationHelper;
import com.dazzle.asklepios.service.helper.PractitionerHelper;
import com.dazzle.asklepios.service.helper.RoomHelper;
import com.dazzle.asklepios.service.helper.ServiceHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.appointment.AppointmentDetailsVM;
import com.dazzle.asklepios.web.rest.vm.appointment.AppointmentLogResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointment.AppointmentQuickAppointmentResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointment.AppointmentTransferVM;
import com.dazzle.asklepios.web.rest.vm.appointment.BulkAppointmentRescheduleResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointment.BulkAppointmentTransferResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointment.BulkReschedulePreviewVM;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentLogRepository appointmentLogRepository;

    private static final String ENTITY_NAME = "Appointment";

    private static final String SYSTEM_CANCEL_REASON = "cancel appointment from reschedule";
    private static final String SYSTEM_RESCHEDULE_REASON = "rescheduled due to availability change ";
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final Logger LOG = LoggerFactory.getLogger(AppointmentService.class);

    // fallback only - real zone is resolved per-facility (see resolveZone), since different
    // facilities can be in different real-world time zones. Relying on ZoneId.systemDefault()
    // here made appointment/encounter times shift by the server's UTC offset in some places.
    @Value("${patient.appointment.scheduling.zone}")
    private String defaultSchedulingZone;

    private ZoneId resolveZone(Long facilityId) {
        return facilityHelper.getFacilityZoneId(facilityId, defaultSchedulingZone);
    }

    private final PatientRepository patientRepository;
    private final DepartmentHelper departmentHelper;
    private final FacilityHelper facilityHelper;
    private final ServiceHelper serviceHelper;
    private final RoomHelper roomHelper;
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
    private final NotificationHelper notificationHelper;

    public List<AppointmentLogResponseVM> getAppointmentLogs(Long appointmentId) {
        LOG.debug("Request to get Appointment Log id={}", appointmentId);

        return appointmentLogRepository
                .findAllByAppointmentIdOrderByLogDateDesc(appointmentId)
                .stream()
                .map(log -> {

                    FacilityDTO facility = log.getFacilityId() == null
                            ? null
                            : facilityHelper.getFacility(log.getFacilityId());

                    String facilityName = facility == null ? null : facility.name();

                    DepartmentDTO department = log.getDepartmentId() == null
                            ? null
                            : departmentHelper.getDepartment(log.getDepartmentId());

                    String departmentName = department == null ? null : department.name();

                    ServiceSetupDTO service = log.getDefaultServiceId() == null
                            ? null
                            : serviceHelper.getService(log.getDefaultServiceId());

                    String serviceName = service == null ? null : service.name();

                    PractitionerDTO practitioner = log.getDefaultPractitionerId() == null
                            ? null
                            : practitionerHelper.getPractitioner(log.getDefaultPractitionerId());

                    String practitionerName = practitioner == null
                            ? null
                            : practitioner.firstName() + " " + practitioner.lastName();

                    Patient patient = log.getPatientId() == null
                            ? null
                            : patientRepository.findById(log.getPatientId()).orElse(null);

                    String patientName = patient == null
                            ? null
                            : patient.getFirstName() + " " + patient.getLastName();
                    String resourceName = null;

                    if (log.getResourceId() != null && log.getResourceType() != null) {

                        switch (log.getResourceType().name()) {

                            case "SERVICE" -> {
                                ServiceSetupDTO r = serviceHelper.getService(log.getResourceId());
                                resourceName = r == null ? null : r.name();
                            }

                            case "ROOM" -> {
                                RoomDTO r = roomHelper.getRoom(log.getResourceId());
                                resourceName = r == null ? null : r.name();
                            }

                            case "PRACTITIONER" -> {
                                PractitionerDTO r = practitionerHelper.getPractitioner(log.getResourceId());
                                resourceName = r == null
                                        ? null
                                        : r.firstName() + " " + r.lastName();
                            }

                            case "DIAGNOSTIC_TEST" -> {
                                DiagnosticTestSetupDTO r = diagnosticTestHelper.getDiagnosticTest(log.getResourceId());
                                resourceName = r == null ? null : r.name();
                            }

                            case "CATALOG" -> {
                                CatalogDTO r = catalogHelper.getCatalog(log.getResourceId());
                                resourceName = r == null ? null : r.name();
                            }
                            case "DEPARTMENT" -> {
                                resourceName = departmentName;
                            }

                            default -> resourceName = null;
                        }
                    }

                    return new AppointmentLogResponseVM(
                            log.getId(),
                            log.getAppointmentId(),
                            log.getOperationType(),
                            log.getLogDate(),
                            log.getLogBy(),

                            log.getFacilityId(),
                            facilityName,

                            log.getDepartmentId(),
                            departmentName,

                            log.getAvailabilityGenerationBatchId(),
                            log.getResourceType(),
                            log.getResourceId(),
                            resourceName,

                            log.getCapacityIndex(),
                            log.getStartDatetime(),
                            log.getEndDatetime(),

                            log.getPatientId(),
                            patientName,

                            log.getDefaultServiceId(),
                            serviceName,

                            log.getDefaultPractitionerId(),
                            practitionerName,

                            log.getReason(),
                            log.getBookingMode(),
                            log.getStatus(),
                            log.getService(),
                            log.getServiceGroupId(),
                            log.getDeferred(),
                            log.getDeferredAt(),
                            log.getRequireConfirmation(),
                            log.getNoShowReason(),
                            log.getCancelReason(),
                            log.getCancelledBy(),
                            log.getPriority(),
                            log.getOriginType(),
                            log.getOriginName(),
                            log.getNote(),
                            log.getFollowUpEncounterId(),
                            log.getBookingGroup() != null ? log.getBookingGroup().getId() : null,
                            log.getWaitingList() != null ? log.getWaitingList().getId() : null,
                            log.getHl7AppointmentNumber(),
                            log.getCreatedDate(),
                            log.getLastModifiedDate(),
                            log.getCreatedBy(),
                            log.getLastModifiedBy()

                    );
                })
                .toList();
    }

    @Transactional
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
        Appointment savedAppointment = appointmentRepository.save(appointment);

        if (savedAppointment.getStatus() == AppointmentStatus.BOOKED) {

            notifyAppointmentEvent(
                    savedAppointment,
                    NotificationCode.APPOINTMENT_BOOKED,
                    null
            );
        }

        return savedAppointment;
    }

    public Page<Appointment> getAppointmentsByStatusBetweenDates(List<AppointmentStatus> status, Instant startDatetime, Instant endDatetime, Pageable pageable) {
        LOG.debug("Request to get appointments with patient not null between startDatetime={} and endDatetime={}", startDatetime, endDatetime);

        return appointmentRepository.findByStatusInAndStartDatetimeBetween(status, startDatetime, endDatetime, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AppointmentDetailsVM> filterAppointment(AppointmentSearchFilterMultiDepartmentDTO filter, Pageable pageable){

        LOG.debug("Service filter Appointments filter={} pageable={}", filter, pageable);

        if (filter.facility() == null) {
            throw new BadRequestAlertException("facility", ENTITY_NAME, "Facility is required");
        }

        List<Long> departmentIds = filter.departmentIds();

        if (departmentIds == null || departmentIds.isEmpty()) {
            String login = currentUsername();
            departmentIds = departmentHelper.getBookableDepartment().stream().map(DepartmentDTO::id).toList();

            if (departmentIds == null || departmentIds.isEmpty()) {
                LOG.debug("[FILTER] No bookable departments found for logged-in user={}", login);
                return Page.empty(pageable);
            }
        }

        List<Long> finalDepartmentIds = departmentIds;

        Specification<Appointment> appointmentFilterSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            query.distinct(true);

            predicates.add(cb.equal(root.get("facilityId"), filter.facility()));

            predicates.add(root.get("departmentId").in(finalDepartmentIds));

            if (filter.resourceType() != null) {
                predicates.add(cb.equal(root.get("resourceType"), filter.resourceType()));
            }

            if (filter.resourceId() != null) {
                predicates.add(cb.equal(root.get("resourceId"), filter.resourceId()));
            }

            if (filter.status() != null && !filter.status().isEmpty()) {
                predicates.add(root.get("status").in(filter.status()));
            }

            if (filter.bookingMode() != null && !filter.bookingMode().isEmpty()) {
                predicates.add(root.get("bookingMode").in(filter.bookingMode()));
            } else {
                predicates.add(cb.notEqual(root.get("bookingMode"), BookingMode.BUFFER));
            }

            if (filter.patientId() != null) {
                predicates.add(cb.equal(root.join("patient", JoinType.LEFT).get("id"), filter.patientId()));
            }
            if (filter.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("startDatetime"),
                        filter.startDate()
                ));
            }

            if (filter.endDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("startDatetime"),
                        filter.endDate()
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Appointment> result = appointmentRepository.findAll(appointmentFilterSpec, pageable);

        LOG.debug(
                "[FILTER] Appointments result totalElements={} totalPages={} pageNumber={} pageSize={}",
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );

        return result.map(this::toAppointmentDetailsVM);
    }

    @Transactional(readOnly = true)
    public Page<Appointment> filterAppointmentByPatientPortal(AppointmentSearchFilterMultiDepartmentDTO filter, Pageable pageable) {

        LOG.debug("Service filter Appointments by patient portal filter={} pageable={}", filter, pageable);

        if (filter.facility() == null) {
            throw new BadRequestAlertException("facility", ENTITY_NAME, "Facility is required");
        }

        List<Long> finalDepartmentIds = filter.departmentIds();

        Specification<Appointment> appointmentFilterSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            query.distinct(true);

            predicates.add(cb.equal(root.get("facilityId"), filter.facility()));

            if (filter.departmentIds() != null && !filter.departmentIds().isEmpty()) {
                predicates.add(
                        root.get("departmentId").in(filter.departmentIds())
                );
            }
            if (filter.resourceType() != null) {
                predicates.add(cb.equal(root.get("resourceType"), filter.resourceType()));
            }

            if (filter.resourceId() != null) {
                predicates.add(cb.equal(root.get("resourceId"), filter.resourceId()));
            }

            if (filter.status() != null && !filter.status().isEmpty()) {
                predicates.add(root.get("status").in(filter.status()));
            }

            if (filter.bookingMode() != null && !filter.bookingMode().isEmpty()) {
                predicates.add(root.get("bookingMode").in(filter.bookingMode()));
            } else {
                predicates.add(cb.notEqual(root.get("bookingMode"), BookingMode.BUFFER));
            }

            if (filter.patientId() != null) {
                predicates.add(cb.equal(root.join("patient", JoinType.LEFT).get("id"), filter.patientId()));
            }
            if (filter.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("startDatetime"),
                        filter.startDate()
                ));
            }

            if (filter.endDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("startDatetime"),
                        filter.endDate()
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Appointment> result = appointmentRepository.findAll(appointmentFilterSpec, pageable);

        LOG.debug(
                "[FILTER] Appointments result totalElements={} totalPages={} pageNumber={} pageSize={}",
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );

        return result;
    }


    public List<Appointment> getAppointmentsByStatusBetweenDatesWithoutPagination(List<AppointmentStatus> status, Instant startDatetime, Instant endDatetime) {
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

    @Transactional(readOnly = true)
    public List<Appointment> filterAppointmentWithoutPagination(AppointmentSearchFilterMultiDepartmentDTO filter) {

        LOG.debug("Service filter Appointments filter={}", filter);

        if (filter.facility() == null) {
            throw new BadRequestAlertException("facility", ENTITY_NAME, "Facility is required");
        }

        List<Long> departmentIds = filter.departmentIds();

        if (departmentIds == null || departmentIds.isEmpty()) {
            String login = currentUsername();
            departmentIds = departmentHelper.getBookableDepartment().stream().map(DepartmentDTO::id).toList();

            if (departmentIds == null || departmentIds.isEmpty()) {
                LOG.debug("[FILTER] No bookable departments found for logged-in user={}", login);
                return Collections.emptyList();
            }
        }

        List<Long> finalDepartmentIds = departmentIds;

        Specification<Appointment> appointmentFilterSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            query.distinct(true);

            predicates.add(cb.equal(root.get("facilityId"), filter.facility()));

            predicates.add(root.get("departmentId").in(finalDepartmentIds));

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

    @Transactional
    public Appointment cancel(AppointmentCancelDTO dto) {
        Appointment appointment = getAppointment(dto.id());

        validateCancelable(appointment);

        DepartmentDTO department = appointment.getDepartmentId() != null
                ? departmentHelper.getDepartment(appointment.getDepartmentId())
                : null;

        Map<String, Object> notificationData =
                buildAppointmentNotificationData(appointment, department);

        notificationData.put(
                "cancel_reason",
                dto.cancelReason() != null ? dto.cancelReason() : ""
        );
        PractitionerDTO practitioner = appointment.getDefaultPractitionerId() != null
                ? practitionerHelper.getPractitioner(appointment.getDefaultPractitionerId())
                : null;

        String login = currentUsername();

        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule =
                notificationHelper.resolveRecipients(appointment.getDepartmentId(), login, appointment.getCreatedBy(), appointment.getPatient(), practitioner, false);

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelReason(dto.cancelReason());
        appointment.setCancelledBy(currentUsername());
        appointment.setService(null);
        appointment.setPatient(null);
        appointment.setReason(null);
        appointment.setNote(null);
        appointment.setPriority(EncounterPriority.NORMAL);
        appointment.setOriginName(null);
        appointment.setOriginType(null);
        appointment.setFollowUpEncounter(null);

        Appointment savedAppointment = appointmentRepository.save(appointment);

        createAppointmentNotification(
                savedAppointment,
                NotificationCode.APPOINTMENT_CANCELLED,
                notificationData,
                recipientsByRule
        );

        return savedAppointment;
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
        Appointment savedAppointment = appointmentRepository.save(appointment);

        notifyAppointmentEvent(savedAppointment, NotificationCode.APPOINTMENT_CONFIRMED, null);

        return savedAppointment;
    }

    @Transactional
    public Appointment undoConfirm(Long id) {
        Appointment appointment = getAppointment(id);

        validateUndoConfirmable(appointment);

        if (appointment.getPatient() == null) {
            throw new BadRequestAlertException("patientrequired", ENTITY_NAME, "Cannot undo confirm appointment without patient");
        }
        appointment.setConfirmedAt(null);
        appointment.setStatus(AppointmentStatus.BOOKED);
        Appointment savedAppointment = appointmentRepository.save(appointment);

        return savedAppointment;
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
        appointment.setHl7AppointmentNumber(appointmentDTO.hl7AppointmentNumber());
        if (appointmentDTO.service() == EncounterReason.FOLLOW_UP && appointmentDTO.followUpEncounterId() != null) {
            PatientEncounter followUpEncounter = patientEncounterRepository.findById(appointmentDTO.followUpEncounterId())
                    .orElseThrow(() -> new BadRequestAlertException("Patient Encounter not found with id: " + appointmentDTO.followUpEncounterId(), ENTITY_NAME, "notfound"));

            appointment.setFollowUpEncounter(followUpEncounter);
        }
        appointment.setCapacityIndex(1);
        Appointment quickAppointment = appointmentRepository.save(appointment);
        PatientEncounter encounter = createEncounter(quickAppointment, department);

        notifyAppointmentEvent(quickAppointment, NotificationCode.QUICK_APPOINTMENT_CREATED, Map.of("quick_appointment", true));

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
    public BulkReschedulePreviewVM getBulkReschedulePreview(Long availabilityGenerationBatchId, boolean includeFreeSlots, Long departmentId, String templateName) {
        LOG.debug(
                "[BULK_RESCHEDULE_PREVIEW] batchId={} includingFreeSlot={} departmentId={} templateName={}",
                availabilityGenerationBatchId,
                includeFreeSlots,
                departmentId,
                templateName
        );

        AvailabilityGenerationBatch previewBatch = getBatch(availabilityGenerationBatchId);

        Instant tomorrowStart = tomorrowStartInstant(resolveZone(previewBatch.getTemplate().getFacilityId()));

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

        AvailabilityGenerationBatch cancelBatch = getBatch(availabilityGenerationBatchId);

        Instant tomorrowStart = tomorrowStartInstant(resolveZone(cancelBatch.getTemplate().getFacilityId()));

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

        Instant tomorrowStart = tomorrowStartInstant(resolveZone(originalBatch.getTemplate().getFacilityId()));

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

    @Transactional(readOnly = true)
    public Page<Appointment> getCancelledAppointmentsBetweenDates(Instant startDatetime, Instant endDatetime, Long departmentId, Pageable pageable) {
        LOG.debug("[CANCELLED_APPOINTMENTS] startDatetime={} endDatetime={} departmentId={} pageable={}",
                startDatetime,
                endDatetime,
                departmentId,
                pageable
        );

        if (departmentId != null) {
            return appointmentRepository
                    .findByStatusAndStartDatetimeBetweenAndDepartmentIdOrderByStartDatetimeAsc(
                            AppointmentStatus.CANCELLED,
                            startDatetime,
                            endDatetime,
                            departmentId,
                            pageable
                    );
        }

        return appointmentRepository
                .findByStatusAndStartDatetimeBetweenOrderByStartDatetimeAsc(
                        AppointmentStatus.CANCELLED,
                        startDatetime,
                        endDatetime,
                        pageable
                );
    }

    @Transactional
    public Appointment createIntegrationAppointment(AppointmentIntegrationCreateDTO dto) {
        validateCreateAppointment(dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "Patient not found",
                        ENTITY_NAME,
                        "patientnotfound"
                ));

        Appointment appointment = appointmentRepository.findFirstByDepartmentIdAndResourceTypeAndResourceIdAndStartDatetimeGreaterThanEqualAndEndDatetimeLessThanEqualAndStatusAndBookingMode(
                dto.departmentId(),
                dto.resourceType(),
                dto.resourceId(),
                dto.startDatetime(),
                dto.endDatetime(),
                AppointmentStatus.NEW,
                BookingMode.SLOT
        ).orElseThrow(() -> new BadRequestAlertException(
                "No available appointment slot found",
                ENTITY_NAME,
                "slotnotfound"
        ));

        if (Boolean.TRUE.equals(appointment.getRequirePractitioner()) && dto.defaultPractitionerId() == null) {
            throw new BadRequestAlertException(
                    "Practitioner is required",
                    ENTITY_NAME,
                    "practitionerrequired"
            );
        }

        appointment.setPatient(patient);

        appointment.setDefaultServiceId(dto.defaultServiceId());
        appointment.setDefaultPractitionerId(dto.defaultPractitionerId());


        appointment.setReason(dto.reason());
        appointment.setService(dto.service());


        appointment.setPriority(dto.priority() != null ? dto.priority() : EncounterPriority.NORMAL);

        appointment.setOriginType(dto.originType());
        appointment.setOriginName(dto.originName());
        appointment.setNote(dto.note());

        appointment.setHl7AppointmentNumber(dto.hl7AppointmentNumber());

        appointment.setStatus(AppointmentStatus.BOOKED);


        if (dto.followUpEncounterId() != null) {
            PatientEncounter encounter = patientEncounterRepository.findById(dto.followUpEncounterId())
                    .orElseThrow(() -> new BadRequestAlertException(
                            "Follow up encounter not found",
                            ENTITY_NAME,
                            "encounternotfound"
                    ));

            appointment.setFollowUpEncounter(encounter);
        }


        Appointment savedAppointment = appointmentRepository.save(appointment);

        notifyAppointmentEvent(
                savedAppointment,
                NotificationCode.APPOINTMENT_BOOKED,
                null
        );


        return savedAppointment;
    }

    public void notifyPatientForAppointmentReschedule(Long originalBatchId) {
        LOG.debug("[BULK_RESCHEDULE] Ask patient notification originalBatchId={}", originalBatchId);
        AvailabilityGenerationBatch originalBatch = getBatch(originalBatchId);

        Instant tomorrowStart = tomorrowStartInstant(resolveZone(originalBatch.getTemplate().getFacilityId()));

        List<Appointment> rescheduledAppointments = appointmentRepository.findByAvailabilityGenerationBatch_IdAndStatusInAndStartDatetimeGreaterThanOrderByStartDatetimeAsc(originalBatchId, List.of(AppointmentStatus.BOOKED, AppointmentStatus.CONFIRMED), tomorrowStart);

        for (Appointment appointment : rescheduledAppointments) {
            //TODO: Add the actual choice_url for the patient to select a new appointment slot
            notifyAppointmentEvent(appointment, NotificationCode.APPOINTMENT_RESCHEDULED_BY_PATIENT, Map.of("choice_url", ""));
        }
    }

    @Transactional(readOnly = true)
    public List<AppointmentTransferVM> getTransferSourceAppointments(LocalDate startDate, LocalDate endDate) {

        validateDates(startDate, endDate);

        Instant start = toStartOfDay(startDate);
        Instant end = toStartOfDay(endDate.plusDays(1));

        List<Appointment> appointments =
                appointmentRepository
                        .findByStartDatetimeGreaterThanEqualAndStartDatetimeLessThanAndStatusInOrderByStartDatetimeAsc(
                                start,
                                end,
                                List.of(
                                        AppointmentStatus.BOOKED,
                                        AppointmentStatus.CONFIRMED
                                )
                        );

        return appointments.stream()
                .filter(appointment -> appointment.getPatient() != null)
                .filter(appointment -> appointment.getDepartmentId() != null)
                .map(this::toAppointmentTransferVM)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentTransferVM> getTransferTargetAppointments(Long departmentId, LocalDate startDate, LocalDate endDate) {
        validateDates(startDate, endDate);

        if (departmentId == null) {
            throw new BadRequestAlertException(
                    "Department is required",
                    "appointment",
                    "department.required"
            );
        }

        Instant start = toStartOfDay(startDate);
        Instant end = toStartOfDay(endDate.plusDays(1));

        List<Appointment> appointments =
                appointmentRepository
                        .findByStartDatetimeGreaterThanEqualAndStartDatetimeLessThanAndStatusAndBookingModeAndDepartmentIdAndPatientIsNullOrderByStartDatetimeAsc(
                                start,
                                end,
                                AppointmentStatus.NEW,
                                BookingMode.SLOT,
                                departmentId
                        );

        return appointments.stream()
                .map(this::toAppointmentTransferVM)
                .toList();
    }

    @Transactional
    public BulkAppointmentTransferResponseVM bulkAppointmentTransfer(BulkAppointmentTransferDTO dto) {

        LOG.info("[BULK_TRANSFER] Starting bulk appointment transfer, count={}", dto.transfers().size());

        if (dto.transfers().isEmpty()) {
            throw new BadRequestAlertException(
                    "At least one appointment transfer is required",
                    ENTITY_NAME,
                    "emptytransfers"
            );
        }

        /*
         * ============================================================
         * STEP 1
         *
         * Validate duplicate source/target IDs.
         *
         * One source appointment cannot be transferred twice.
         * One target appointment cannot receive two patients.
         * ============================================================
         */

        Set<Long> sourceIds = new HashSet<>();
        Set<Long> targetIds = new HashSet<>();

        for (AppointmentTransferMappingDTO mapping : dto.transfers()) {

            if (!sourceIds.add(mapping.oldAppointmentId())) {
                throw new BadRequestAlertException(
                        "Source appointment "
                                + mapping.oldAppointmentId()
                                + " appears more than once",
                        ENTITY_NAME,
                        "duplicatesource"
                );
            }

            if (!targetIds.add(mapping.newAppointmentId())) {
                throw new BadRequestAlertException(
                        "Target appointment "
                                + mapping.newAppointmentId()
                                + " appears more than once",
                        ENTITY_NAME,
                        "duplicatetarget"
                );
            }

            if (mapping.oldAppointmentId()
                    .equals(mapping.newAppointmentId())) {

                throw new BadRequestAlertException(
                        "Old and new appointment cannot be the same",
                        ENTITY_NAME,
                        "sameappointment"
                );
            }
        }

        /*
         * ============================================================
         * STEP 2
         *
         * Load ALL appointments before modifying anything.
         *
         * This gives us all-or-nothing behavior.
         * ============================================================
         */

        Map<Long, Appointment> appointments =
                appointmentRepository
                        .findAllById(
                                Stream.concat(
                                        sourceIds.stream(),
                                        targetIds.stream()
                                ).collect(Collectors.toSet())
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                Appointment::getId,
                                Function.identity()
                        ));

        /*
         * ============================================================
         * STEP 3
         *
         * Make sure all IDs exist.
         * ============================================================
         */

        for (AppointmentTransferMappingDTO mapping : dto.transfers()) {

            if (!appointments.containsKey(mapping.oldAppointmentId())) {

                throw new NotFoundAlertException(
                        "Source appointment not found with id "
                                + mapping.oldAppointmentId(),
                        ENTITY_NAME,
                        "appointmentnotfound"
                );
            }

            if (!appointments.containsKey(mapping.newAppointmentId())) {

                throw new NotFoundAlertException(
                        "Target appointment not found with id "
                                + mapping.newAppointmentId(),
                        ENTITY_NAME,
                        "appointmentnotfound"
                );
            }
        }

        /*
         * ============================================================
         * STEP 4
         *
         * Validate EVERY mapping BEFORE changing anything.
         *
         * This is important.
         *
         * If mapping #5 is invalid, mappings #1-#4 must NOT already
         * have been transferred.
         * ============================================================
         */

        for (AppointmentTransferMappingDTO mapping : dto.transfers()) {

            Appointment oldAppointment = appointments.get(mapping.oldAppointmentId());

            Appointment newAppointment = appointments.get(mapping.newAppointmentId());

            validateAppointmentTransfer(oldAppointment, newAppointment);
        }

        /*
         * ============================================================
         * STEP 5
         *
         * Everything is valid.
         *
         * Now perform the transfers.
         * ============================================================
         */

        String username = currentUsername();

        List<Long> transferredOldIds = new ArrayList<>();

        List<Long> transferredNewIds = new ArrayList<>();

        for (AppointmentTransferMappingDTO mapping : dto.transfers()) {

            Appointment oldAppointment = appointments.get(mapping.oldAppointmentId());

            Appointment newAppointment = appointments.get(mapping.newAppointmentId());

            executeAppointmentTransfer(oldAppointment, newAppointment, username);

            transferredOldIds.add(oldAppointment.getId());

            transferredNewIds.add(newAppointment.getId());
        }

        LOG.info("[BULK_TRANSFER] Successfully transferred {} appointments", transferredOldIds.size());

        return new BulkAppointmentTransferResponseVM(
                true,
                "Appointments transferred successfully",
                transferredOldIds.size(),
                transferredOldIds,
                transferredNewIds
        );
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BadRequestAlertException(
                    "Start date and end date are required",
                    "appointment",
                    "date.required"
            );
        }

        if (startDate.isAfter(endDate)) {
            throw new BadRequestAlertException(
                    "Start date cannot be after end date",
                    "appointment",
                    "date.invalid"
            );
        }
    }

    private Instant toStartOfDay(LocalDate date) {
        return date
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
    }

    private AppointmentTransferVM toAppointmentTransferVM(
            Appointment appointment
    ) {
        String patientName = null;
        String medicalRecordNumber = null;
        Long patientId = null;

        if (appointment.getPatient() != null) {
            patientId = appointment.getPatient().getId();

            medicalRecordNumber =
                    appointment.getPatient().getMedicalRecordNumber();

            patientName = appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName();
        }

        String practitionerName = null;

        /*
         * Use your existing practitioner lookup/helper here.
         *
         * For example, if practitioner information is already available
         * through the appointment/entity relationship, use it directly.
         */

        String departmentName = null;

        /*
         * Same here: resolve department name using your existing
         * department helper/repository if needed.
         */

        return new AppointmentTransferVM(
                appointment.getId(),

                patientId,
                patientName,
                medicalRecordNumber,

                appointment.getDepartmentId(),
                departmentName,

                appointment.getDefaultPractitionerId(),
                practitionerName,

                appointment.getResourceType(),
                appointment.getResourceId(),

                appointment.getStartDatetime(),
                appointment.getEndDatetime(),

                appointment.getStatus(),
                appointment.getBookingMode()
        );
    }


    private void executeAppointmentTransfer(Appointment oldAppointment, Appointment newAppointment, String username) {

        LOG.debug("[APPOINTMENT_TRANSFER] oldAppointmentId={}, newAppointmentId={}", oldAppointment.getId(), newAppointment.getId());

        /*
         * ============================================================
         * KEEP TARGET CONFIGURATION
         *
         * DO NOT copy these from old appointment:
         *
         * - facility
         * - department
         * - resourceType
         * - resourceId
         * - practitioner
         * - startDatetime
         * - endDatetime
         * - bookingMode
         *
         * The user selected this exact target appointment,
         * therefore its configuration must remain unchanged.
         * ============================================================
         */

        /*
         * Move patient.
         */
        newAppointment.setPatient(oldAppointment.getPatient());

        /*
         * Move patient-related appointment information.
         */
        newAppointment.setReason(oldAppointment.getReason());

        newAppointment.setNote(oldAppointment.getNote());

        newAppointment.setPriority(oldAppointment.getPriority());

        newAppointment.setOriginType(oldAppointment.getOriginType());

        newAppointment.setOriginName(oldAppointment.getOriginName());

        newAppointment.setService(oldAppointment.getService());

        newAppointment.setFollowUpEncounter(oldAppointment.getFollowUpEncounter());

        /*
         * Reset target state.
         */
        newAppointment.setCancelReason(null);
        newAppointment.setCancelledBy(null);
        newAppointment.setNoShowReason(null);
        newAppointment.setConfirmedAt(null);
        newAppointment.setCheckedInAt(null);

        /*
         * Target is now booked.
         */
        newAppointment.setStatus(AppointmentStatus.BOOKED);

        /*
         * ============================================================
         * OLD APPOINTMENT
         * ============================================================
         */

        oldAppointment.setStatus(AppointmentStatus.RESCHEDULED);

        oldAppointment.setPatient(null);
        oldAppointment.setReason(null);
        oldAppointment.setNote(null);
        oldAppointment.setService(null);
        oldAppointment.setPriority(EncounterPriority.NORMAL);
        oldAppointment.setOriginName(null);
        oldAppointment.setOriginType(null);
        oldAppointment.setFollowUpEncounter(null);

        /*
         * Save.
         */
        appointmentRepository.save(oldAppointment);
        appointmentRepository.save(newAppointment);

        /*
         * ============================================================
         * RESCHEDULE HISTORY
         * ============================================================
         */

        AppointmentReschedule reschedule = new AppointmentReschedule();

        reschedule.setOldAppointmentId(oldAppointment.getId());

        reschedule.setNewAppointmentId(newAppointment.getId());

        reschedule.setRescheduleReason("Appointment transferred by user");

        reschedule.setCreatedBy(username);

        appointmentRescheduleRepository.save(reschedule);

        /*
         * ============================================================
         * NOTIFICATION
         * ============================================================
         */

        notifyAppointmentEvent(newAppointment, NotificationCode.APPOINTMENT_RESCHEDULED, Map.of(
                        "old_appointment_id",
                        oldAppointment.getId(),

                        "new_appointment_id",
                        newAppointment.getId(),

                        "old_appointment_date",
                        oldAppointment.getStartDatetime() != null
                                ? formatter.format(
                                oldAppointment.getStartDatetime()
                        )
                                : "",

                        "old_appointment_end_date",
                        oldAppointment.getEndDatetime() != null
                                ? formatter.format(
                                oldAppointment.getEndDatetime()
                        )
                                : "",

                        "new_appointment_Date",
                        newAppointment.getStartDatetime() != null
                                ? formatter.format(
                                newAppointment.getStartDatetime()
                        )
                                : "",

                        "new_appointment_end_date",
                        newAppointment.getEndDatetime() != null
                                ? formatter.format(
                                newAppointment.getEndDatetime()
                        )
                                : "",

                        "reschedule_reason",
                        "Appointment transferred by user"
                )
        );
    }

    private void validateAppointmentTransfer(Appointment oldAppointment, Appointment newAppointment) {

        /*
         * ============================================================
         * SOURCE
         * ============================================================
         */

        if (oldAppointment.getStatus() != AppointmentStatus.BOOKED
                && oldAppointment.getStatus() != AppointmentStatus.CONFIRMED) {

            throw new BadRequestAlertException(
                    "Source appointment "
                            + oldAppointment.getId()
                            + " must be BOOKED or CONFIRMED",
                    ENTITY_NAME,
                    "invalidsourcestatus"
            );
        }

        if (oldAppointment.getPatient() == null) {

            throw new BadRequestAlertException(
                    "Source appointment "
                            + oldAppointment.getId()
                            + " does not have a patient",
                    ENTITY_NAME,
                    "patientrequired"
            );
        }

        if (oldAppointment.getDepartmentId() == null) {

            throw new BadRequestAlertException(
                    "Source appointment "
                            + oldAppointment.getId()
                            + " does not have a department",
                    ENTITY_NAME,
                    "departmentrequired"
            );
        }

        /*
         * ============================================================
         * TARGET
         * ============================================================
         */

        if (newAppointment.getStatus() != AppointmentStatus.NEW) {

            throw new BadRequestAlertException(
                    "Target appointment "
                            + newAppointment.getId()
                            + " is not available",
                    ENTITY_NAME,
                    "targetnotavailable"
            );
        }

        if (newAppointment.getBookingMode() != BookingMode.SLOT) {

            throw new BadRequestAlertException(
                    "Target appointment "
                            + newAppointment.getId()
                            + " must be a SLOT appointment",
                    ENTITY_NAME,
                    "invalidtargetbookingmode"
            );
        }

        /*
         * ============================================================
         * SAME DEPARTMENT
         *
         * This is the ONLY configuration matching rule.
         *
         * We intentionally DO NOT check:
         *
         * practitioner
         * room
         * resource
         * resourceType
         * time
         * service
         *
         * because those belong to the target appointment selected
         * by the user.
         * ============================================================
         */

        if (!Objects.equals(
                oldAppointment.getDepartmentId(),
                newAppointment.getDepartmentId()
        )) {

            throw new BadRequestAlertException(
                    "Source appointment "
                            + oldAppointment.getId()
                            + " and target appointment "
                            + newAppointment.getId()
                            + " must belong to the same department",
                    ENTITY_NAME,
                    "departmentmismatch"
            );
        }

        /*
         * ============================================================
         * TARGET MUST BE EMPTY
         * ============================================================
         */

        if (newAppointment.getPatient() != null) {

            throw new BadRequestAlertException(
                    "Target appointment "
                            + newAppointment.getId()
                            + " already has a patient",
                    ENTITY_NAME,
                    "targetoccupied"
            );
        }
    }

    private void validateCreateAppointment(AppointmentIntegrationCreateDTO dto) {

        if (dto.startDatetime().isAfter(dto.endDatetime())) {
            throw new BadRequestAlertException(
                    "Start datetime must be before end datetime",
                    ENTITY_NAME,
                    "invaliddatetime"
            );
        }

        if (dto.startDatetime().isBefore(Instant.now())) {
            throw new BadRequestAlertException(
                    "Appointment cannot be created in the past",
                    ENTITY_NAME,
                    "pastdatetime"
            );
        }

        if (dto.service() == EncounterReason.FOLLOW_UP
                && dto.followUpEncounterId() == null) {
            throw new BadRequestAlertException(
                    "Follow up encounter is required",
                    ENTITY_NAME,
                    "followuprequired"
            );
        }

        if (!patientRepository.existsById(dto.patientId())) {
            throw new BadRequestAlertException(
                    "Patient not found",
                    ENTITY_NAME,
                    "patientnotfound"
            );
        }

        if (dto.defaultPractitionerId() != null &&
                practitionerHelper.getPractitioner(dto.defaultPractitionerId()) == null) {
            throw new BadRequestAlertException(
                    "Practitioner not found",
                    ENTITY_NAME,
                    "practitionernotfound"
            );
        }

        if (departmentHelper.getDepartment(dto.departmentId()) == null) {
            throw new BadRequestAlertException(
                    "Department not found",
                    ENTITY_NAME,
                    "departmentnotfound"
            );
        }

        validateResource(dto);
    }

    private void validateResource(AppointmentIntegrationCreateDTO dto) {

        switch (dto.resourceType()) {

            case PRACTITIONER -> practitionerHelper.getPractitioner(dto.resourceId());

            case SERVICE -> serviceHelper.getService(dto.resourceId());

            case ROOM -> roomHelper.getRoom(dto.resourceId());

            case DEPARTMENT -> departmentHelper.getDepartment(dto.resourceId());

            case DIAGNOSTIC_TEST -> diagnosticTestHelper.getDiagnosticTest(dto.resourceId());

            case CATALOG -> catalogHelper.getCatalog(dto.resourceId());

            default -> throw new BadRequestAlertException(
                    "Unsupported resource type",
                    ENTITY_NAME,
                    "invalidresource"
            );
        }
    }

    private Instant tomorrowStartInstant(ZoneId zone) {
        return LocalDate.now(zone)
                .plusDays(1)
                .atStartOfDay(zone)
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

        Instant oldStartDatetime = oldAppointment.getStartDatetime();
        Instant oldEndDatetime = oldAppointment.getEndDatetime();

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

        notifyAppointmentEvent(savedNewAppointment, NotificationCode.APPOINTMENT_RESCHEDULED,
                Map.of(
                        "old_appointment_id", savedOldAppointment.getId(),
                        "new_appointment_id", savedNewAppointment.getId(),
                        "old_appointment_date", oldStartDatetime != null ? formatter.format(oldStartDatetime) : "",
                        "old_appointment_end_date", oldEndDatetime != null ? formatter.format(oldEndDatetime) : "",
                        "new_appointment_Date", savedNewAppointment.getStartDatetime() != null ? formatter.format(savedNewAppointment.getStartDatetime()) : "",
                        "new_appointment_end_date", savedNewAppointment.getEndDatetime() != null ? formatter.format(savedNewAppointment.getEndDatetime()) : "",
                        "reschedule_reason", rescheduleReason != null ? rescheduleReason : ""
                )
        );
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
                null,
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
                    null,
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
                                .atZone(resolveZone(savedAppointment.getFacilityId()))
                                .toLocalDate(),

                        savedAppointment.getStartDatetime()
                                .atZone(resolveZone(savedAppointment.getFacilityId()))
                                .toLocalTime(),

                        TreatmentStatus.NEW,

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
        } else if (appointment.getStatus() == AppointmentStatus.NO_SHOW) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "no-show appointment cannot be cancelled");
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
        } else if (appointment.getStatus() == AppointmentStatus.NO_SHOW) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "appointment already no-show");
        }
    }

    private void validateConfirmable(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Only booked appointments can be confirmed");
        }
    }

    private void validateUndoConfirmable(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BadRequestAlertException("invalidstatus", ENTITY_NAME, "Only confirmed appointments can be booked when undo confirm");
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

        ZoneId checkInZone = resolveZone(appointment.getFacilityId());
        LocalDate appointmentDate = appointment.getStartDatetime()
                .atZone(checkInZone)
                .toLocalDate();

        LocalDate today = LocalDate.now(checkInZone);

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

    //Notification helper

    private void notifyAppointmentEvent(Appointment appointment, NotificationCode notificationCode, Map<String, Object> extraData) {
        if (appointment == null || notificationCode == null) {
            return;
        }

        DepartmentDTO department = appointment.getDepartmentId() != null
                ? departmentHelper.getDepartment(appointment.getDepartmentId())
                : null;

        Map<String, Object> data = buildAppointmentNotificationData(appointment, department);

        if (extraData != null && !extraData.isEmpty()) {
            data.putAll(extraData);
        }
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);
        PractitionerDTO practitioner = appointment.getDefaultPractitionerId() != null
                ? practitionerHelper.getPractitioner(appointment.getDefaultPractitionerId())
                : null;
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = notificationHelper.resolveRecipients(appointment.getDepartmentId(), login, appointment.getCreatedBy(), appointment.getPatient(), practitioner, false);

        if (recipientsByRule.isEmpty()) {
            LOG.warn("Skip appointment notification because no recipients were resolved. appointmentId={}, code={}", appointment.getId(), notificationCode);
            return;
        }


        try {
            LOG.debug("Creating appointment notification. appointmentId={}, code={}, recipientsByRule={}", appointment.getId(), notificationCode, recipientsByRule);

            notificationHelper.sendNotification(appointment.getFacilityId(), notificationCode, recipientsByRule, data, "APPOINTMENT", appointment.getId());

        } catch (Exception e) {
            LOG.warn("Failed to create appointment notification. appointmentId={}, code={}, error={}", appointment.getId(), notificationCode, e.getMessage());
        }
    }

    private Map<String, Object> buildAppointmentNotificationData(Appointment appointment, DepartmentDTO department) {
        Map<String, Object> data = new LinkedHashMap<>();
        FacilityDTO facilityDTO = null;
        if (appointment.getFacilityId() != null) {
            facilityDTO = facilityHelper.getFacility(appointment.getFacilityId());
        }
        data.put("facility_name", facilityDTO != null ? facilityDTO.name() : "");
        data.put("appointment_id", appointment.getId());
        data.put("appointment_number", appointment.getId());
        data.put("department_id", appointment.getDepartmentId());
        data.put("department_name", department != null ? department.name() : "");
        data.put("patient_name", appointment.getPatient() != null
                ? notificationHelper.getPatientName(appointment.getPatient())
                : "");

        data.put("appointment_date", appointment.getStartDatetime() != null
                ? formatter.format(appointment.getStartDatetime())
                : "");
        return data;
    }

    private void createAppointmentNotification(Appointment appointment, NotificationCode notificationCode, Map<String, Object> data, Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule) {
        if (appointment == null || notificationCode == null) {
            return;
        }

        if (recipientsByRule == null || recipientsByRule.isEmpty()) {
            LOG.warn(
                    "Skip appointment notification because no recipients were resolved. appointmentId={}, code={}",
                    appointment.getId(),
                    notificationCode
            );
            return;
        }

        try {
            LOG.debug(
                    "Creating appointment notification. appointmentId={}, code={}, recipientsByRule={}",
                    appointment.getId(),
                    notificationCode,
                    recipientsByRule
            );

            notificationHelper.sendNotification(appointment.getFacilityId(),
                    notificationCode,
                    recipientsByRule,
                    data,
                    "APPOINTMENT",
                    appointment.getId());
        } catch (Exception e) {
            LOG.warn(
                    "Failed to create appointment notification. appointmentId={}, code={}, error={}",
                    appointment.getId(),
                    notificationCode,
                    e.getMessage()
            );
        }
    }
    private AppointmentDetailsVM toAppointmentDetailsVM(Appointment appointment) {

        String resourceName = null;

        if (appointment.getResourceId() != null && appointment.getResourceType() != null) {

            switch (appointment.getResourceType().name()) {

                case "SERVICE" -> {
                    ServiceSetupDTO resource = serviceHelper.getService(appointment.getResourceId());
                    resourceName = resource == null ? null : resource.name();
                }

                case "ROOM" -> {
                    RoomDTO resource = roomHelper.getRoom(appointment.getResourceId());
                    resourceName = resource == null ? null : resource.name();
                }

                case "PRACTITIONER" -> {
                    PractitionerDTO resource = practitionerHelper.getPractitioner(appointment.getResourceId());
                    resourceName = resource == null
                            ? null
                            : resource.firstName() + " " + resource.lastName();
                }

                case "DIAGNOSTIC_TEST" -> {
                    DiagnosticTestSetupDTO resource =
                            diagnosticTestHelper.getDiagnosticTest(appointment.getResourceId());

                    resourceName = resource == null ? null : resource.name();
                }

                case "CATALOG" -> {
                    CatalogDTO resource = catalogHelper.getCatalog(appointment.getResourceId());
                    resourceName = resource == null ? null : resource.name();
                }

                case "DEPARTMENT" -> {
                    DepartmentDTO resource = departmentHelper.getDepartment(appointment.getResourceId());
                    resourceName = resource == null ? null : resource.name();
                }

                default -> resourceName = null;
            }
        }

        return new AppointmentDetailsVM(
                appointment.getId(),
                appointment.getFacilityId(),
                appointment.getDepartmentId(),
                appointment.getAvailabilityGenerationBatch() != null
                        ? appointment.getAvailabilityGenerationBatch().getId()
                        : null,
                appointment.getResourceType(),
                appointment.getResourceId(),
                resourceName,
                appointment.getCapacityIndex(),
                appointment.getStartDatetime(),
                appointment.getEndDatetime(),
                appointment.getPatient() != null
                        ? appointment.getPatient().getId()
                        : null,
                appointment.getDefaultServiceId(),
                appointment.getDefaultPractitionerId(),
                appointment.getRequirePractitioner(),
                appointment.getReason(),
                appointment.getService(),
                appointment.getServiceGroupId(),
                appointment.getBookingMode(),
                appointment.getStatus(),
                appointment.getDeferred(),
                appointment.getDeferredAt(),
                appointment.getRequireConfirmation(),
                appointment.getNoShowReason(),
                appointment.getCancelReason(),
                appointment.getCancelledBy(),
                appointment.getPriority(),
                appointment.getOriginType(),
                appointment.getOriginName(),
                appointment.getNote(),
                appointment.getFollowUpEncounter() != null
                        ? appointment.getFollowUpEncounter().getId()
                        : null,
                appointment.getConfirmedAt(),
                appointment.getCheckedInAt(),
                appointment.getBookingGroup() != null
                        ? appointment.getBookingGroup().getId()
                        : null,
                appointment.getWaitingList() != null
                        ? appointment.getWaitingList().getId()
                        : null,
                appointment.getHl7AppointmentNumber()
        );
    }
}