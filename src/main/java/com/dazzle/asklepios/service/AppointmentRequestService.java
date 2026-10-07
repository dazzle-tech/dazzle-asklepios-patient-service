package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.FacilityDTO;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.AppointmentRequest;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.AppointmentRequestStatus;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.DayOfWeek;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.RecurrenceUnit;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.repository.AppointmentRequestRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.appointment.AppointmentBookPatientDTO;
import com.dazzle.asklepios.service.dto.appointmentRequest.AppointmentRequestCancelDTO;
import com.dazzle.asklepios.service.dto.appointmentRequest.AppointmentRequestCreateDTO;
import com.dazzle.asklepios.service.dto.appointmentRequest.AppointmentRequestUpdateDTO;
import com.dazzle.asklepios.service.dto.appointmentRequest.RecurringAppointmentRequestDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.service.helper.NotificationHelper;
import com.dazzle.asklepios.service.helper.PractitionerHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.appointmentRequest.AppointmentRequestResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointmentRequest.RecurringAppointmentDayVM;
import com.dazzle.asklepios.web.rest.vm.appointmentRequest.RecurringAppointmentPreviewVM;
import com.dazzle.asklepios.web.rest.vm.appointmentRequest.RecurringAppointmentRequestCreateResponseVM;
import com.dazzle.asklepios.web.rest.vm.appointmentRequest.RecurringAvailableSlotVM;
import com.dazzle.asklepios.web.rest.vm.appointmentRequest.RecurringSkippedDayVM;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AppointmentRequestService {

    private static final String ENTITY_NAME = "appointmentRequest";

    private final AppointmentRequestRepository appointmentRequestRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final FacilityHelper facilityHelper;
    private final DepartmentHelper departmentHelper;
    private final NotificationHelper notificationHelper;
    private final PractitionerHelper practitionerHelper;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(ZoneId.systemDefault());

    public AppointmentRequestResponseVM create(AppointmentRequestCreateDTO dto) {
        return persist(dto, null);
    }

    private AppointmentRequestResponseVM persist(
            AppointmentRequestCreateDTO dto,
            RecurringAppointmentRequestDTO recurring
    ) {
        log.debug("Request to create AppointmentRequest dto={} recurring={}", dto, recurring != null);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id: " + dto.patientId(),
                        ENTITY_NAME,
                        "patient.notfound"
                ));

        PatientEncounter sourceEncounter = patientEncounterRepository.findById(dto.sourceEncounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient encounter not found with id: " + dto.sourceEncounterId(),
                        ENTITY_NAME,
                        "sourceEncounter.notfound"
                ));
        facilityHelper.validateFacilityExists(dto.facilityId());
        departmentHelper.validateDepartmentExists(dto.departmentId());


        AppointmentRequest request = new AppointmentRequest();
        request.setPatient(patient);
        request.setFacilityId(dto.facilityId());
        request.setDepartmentId(dto.departmentId());
        request.setSourceEncounter(sourceEncounter);
        request.setRequestedResourceType(dto.requestedResourceType());
        request.setRequestedResourceId(dto.requestedResourceId());
        request.setPriority(dto.priority());
        request.setReason(dto.reason());
        request.setNote(dto.note());
        request.setStatus(AppointmentRequestStatus.REQUESTED);
        request.setPreferredDate(dto.preferredDate());
        request.setPreferredStartTime(dto.preferredStartTime());
        request.setPreferredEndTime(dto.preferredEndTime());
        request.setRecurring(recurring != null);
        if (recurring != null) {
            request.setRecurrenceDays(formatRecurrenceDays(recurring.daysOfWeek()));
            request.setRecurrenceStartDate(recurring.startDate());
            request.setRecurrencePeriod(recurring.period());
            request.setRecurrenceUnit(recurring.periodUnit());
        }

        AppointmentRequest saved = appointmentRequestRepository.save(request);

        notifyAppointmentRequestEvent(
                saved,
                NotificationCode.APPOINTMENT_REQUEST_CREATED,
                null
        );

        return toResponseVM(saved);    }

    @Transactional(readOnly = true)
    public RecurringAppointmentPreviewVM previewRecurring(RecurringAppointmentRequestDTO dto) {
        log.debug("Request to preview recurring AppointmentRequests dto={}", dto);
        RecurringPlan plan = buildRecurringPlan(dto);
        return toPreview(plan);
    }

    public RecurringAppointmentRequestCreateResponseVM createRecurring(RecurringAppointmentRequestDTO dto) {
        log.debug("Request to create recurring AppointmentRequests dto={}", dto);

        if (dto.priority() == null) {
            throw new BadRequestAlertException(
                    "Priority is required",
                    ENTITY_NAME,
                    "priorityrequired"
            );
        }

        RecurringPlan plan = buildRecurringPlan(dto);
        List<Appointment> selectedSlots = resolveSelectedSlots(dto.selectedAppointmentIds(), plan);
        if (selectedSlots.isEmpty()) {
            throw new BadRequestAlertException(
                    "No days with an available slot can be requested",
                    ENTITY_NAME,
                    "noslots"
            );
        }

        List<AppointmentRequestResponseVM> created = new ArrayList<>();
        for (Appointment slot : selectedSlots) {
            created.add(persist(new AppointmentRequestCreateDTO(
                    dto.patientId(),
                    dto.facilityId(),
                    dto.departmentId(),
                    dto.sourceEncounterId(),
                    dto.requestedResourceType(),
                    dto.requestedResourceId(),
                    dto.priority(),
                    dto.reason(),
                    dto.note(),
                    toLocalDate(slot.getStartDatetime()),
                    slot.getStartDatetime(),
                    slot.getEndDatetime()
            ), dto));
        }

        int daysWithoutSlot = (int) plan.days().stream()
                .filter(day -> day.slot() == null)
                .count();

        return new RecurringAppointmentRequestCreateResponseVM(created, plan.skippedDays(), daysWithoutSlot);
    }

    public AppointmentRequestResponseVM approve(AppointmentRequestUpdateDTO dto) {
        log.debug("Request to approve AppointmentRequest dto={}", dto);

        AppointmentRequest request = appointmentRequestRepository.findById(dto.id())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Appointment request not found with id: " + dto.id(),
                        ENTITY_NAME,
                        "id.notfound"
                ));

        if (request.getStatus() == AppointmentRequestStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled request cannot be approved",
                    ENTITY_NAME,
                    "invalidstatus"
            );
        }

        if (request.getStatus() == AppointmentRequestStatus.APPROVED) {
            throw new BadRequestAlertException(
                    "Appointment request already approved",
                    ENTITY_NAME,
                    "alreadyapproved"
            );
        }

        if (dto.appointmentId() == null) {
            throw new BadRequestAlertException(
                    "Appointment id is required for approval",
                    ENTITY_NAME,
                    "appointmentidrequired"
            );
        }

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id: " + dto.patientId(),
                        ENTITY_NAME,
                        "patient.notfound"
                ));

        PatientEncounter sourceEncounter = patientEncounterRepository.findById(dto.sourceEncounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient encounter not found with id: " + dto.sourceEncounterId(),
                        ENTITY_NAME,
                        "sourceEncounter.notfound"
                ));

        Appointment appointment = appointmentRepository.findById(dto.appointmentId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Appointment not found with id: " + dto.appointmentId(),
                        ENTITY_NAME,
                        "appointment.notfound"
                ));

        AppointmentBookPatientDTO bookDto = new AppointmentBookPatientDTO(
                appointment.getId(),
                patient.getId(),
                appointment.getDefaultServiceId(),
                appointment.getDefaultPractitionerId(),
                dto.reason(),
                AppointmentStatus.BOOKED,
                dto.note(),
                EncounterReason.FOLLOW_UP,
                dto.priority(),
                null,
                null,
                sourceEncounter.getId()
        );

        Appointment bookedAppointment = appointmentService.bookPatientAppointment(bookDto);

        request.setPatient(patient);
        request.setFacilityId(dto.facilityId());
        request.setDepartmentId(dto.departmentId());
        request.setSourceEncounter(sourceEncounter);
        request.setRequestedResourceType(dto.requestedResourceType());
        request.setRequestedResourceId(dto.requestedResourceId());
        request.setPriority(dto.priority());
        request.setReason(dto.reason());
        request.setNote(dto.note());
        request.setAppointment(bookedAppointment);
        request.setStatus(AppointmentRequestStatus.APPROVED);

        AppointmentRequest saved = appointmentRequestRepository.save(request);

        notifyAppointmentRequestEvent(
                saved,
                NotificationCode.APPOINTMENT_REQUEST_APPROVED,
                null
        );

        return toResponseVM(saved);    }

    @Transactional(readOnly = true)
    public AppointmentRequestResponseVM getById(Long id) {
        log.debug("Request to get AppointmentRequest id={}", id);

        AppointmentRequest request = appointmentRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Appointment request not found with id: " + id,
                        ENTITY_NAME,
                        "id.notfound"
                ));

        return toResponseVM(request);
    }

    @Transactional(readOnly = true)
    public List<AppointmentRequestResponseVM> getAll() {
        log.debug("Request to get all AppointmentRequests");

        return appointmentRequestRepository.findAll()
                .stream()
                .map(this::toResponseVM)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentRequestResponseVM> getByPatientId(Long patientId) {
        log.debug("Request to get AppointmentRequests by patientId={}", patientId);

        return appointmentRequestRepository.findByPatientId(patientId)
                .stream()
                .map(this::toResponseVM)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentRequestResponseVM> getByFacilityIdAndBookableDepartment(Long facilityId) {
        log.debug("Request to get AppointmentRequests by facilityId={}", facilityId);

        String login = SecurityUtils
                .getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "user",
                        ENTITY_NAME,
                        "Current user is required"
                ));

        List<Long> bookableDepartmentIds =departmentHelper.getBookableDepartment().stream().map(DepartmentDTO::id).toList();

        if (bookableDepartmentIds == null || bookableDepartmentIds.isEmpty()) {
            log.debug("[APPOINTMENT_REQUEST] No bookable departments found for logged-in user={}", login);
            return Collections.emptyList();
        }

        return appointmentRequestRepository
                .findByFacilityIdAndDepartmentIdIn(facilityId, bookableDepartmentIds)
                .stream()
                .map(this::toResponseVM)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentRequestResponseVM> getBySourceEncounterId(Long sourceEncounterId) {
        log.debug("Request to get AppointmentRequests by sourceEncounterId={}", sourceEncounterId);

        return appointmentRequestRepository.findBySourceEncounterId(sourceEncounterId)
                .stream()
                .map(this::toResponseVM)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentRequestResponseVM> getByStatus(AppointmentRequestStatus status) {
        log.debug("Request to get AppointmentRequests by status={}", status);

        return appointmentRequestRepository.findByStatus(status)
                .stream()
                .map(this::toResponseVM)
                .toList();
    }

    public AppointmentRequestResponseVM approve(Long id) {
        log.debug("Request to approve AppointmentRequest id={}", id);

        AppointmentRequest request = getRequest(id);

        if (request.getStatus() == AppointmentRequestStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled request cannot be approved",
                    ENTITY_NAME,
                    "invalidstatus"
            );
        }

        if (request.getStatus() == AppointmentRequestStatus.APPROVED) {
            throw new BadRequestAlertException(
                    "Appointment request already approved",
                    ENTITY_NAME,
                    "alreadyapproved"
            );
        }

        request.setStatus(AppointmentRequestStatus.APPROVED);

        AppointmentRequest saved = appointmentRequestRepository.save(request);
        return toResponseVM(saved);
    }

    public AppointmentRequestResponseVM cancel(Long id, AppointmentRequestCancelDTO dto) {
        log.debug("Request to cancel AppointmentRequest id={} dto={}", id, dto);

        AppointmentRequest request = getRequest(id);

        if (request.getStatus() == AppointmentRequestStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Appointment request already cancelled",
                    ENTITY_NAME,
                    "alreadycancelled"
            );
        }

        request.setStatus(AppointmentRequestStatus.CANCELLED);
        request.setCancelReason(dto.cancelReason());
        request.setCancelledAt(Instant.now());

        AppointmentRequest saved = appointmentRequestRepository.save(request);
        return toResponseVM(saved);
    }

    public void delete(Long id) {
        log.debug("Request to delete AppointmentRequest id={}", id);

        AppointmentRequest request = getRequest(id);
        appointmentRequestRepository.delete(request);
    }

    @Transactional(readOnly = true)
    public long countRequested() {
        log.debug("Request to count requested AppointmentRequests");

        return appointmentRequestRepository.countAppointmentRequestByStatus(
                AppointmentRequestStatus.REQUESTED
        );
    }

    private RecurringPlan buildRecurringPlan(RecurringAppointmentRequestDTO dto) {
        validateRecurringPeriod(dto);

        patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id: " + dto.patientId(),
                        ENTITY_NAME,
                        "patient.notfound"
                ));
        patientEncounterRepository.findById(dto.sourceEncounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient encounter not found with id: " + dto.sourceEncounterId(),
                        ENTITY_NAME,
                        "sourceEncounter.notfound"
                ));
        facilityHelper.validateFacilityExists(dto.facilityId());
        departmentHelper.validateDepartmentExists(dto.departmentId());

        Set<DayOfWeek> selectedDays = EnumSet.copyOf(dto.daysOfWeek());
        LocalDate rangeStart = dto.startDate();
        LocalDate rangeEnd = dto.periodUnit() == RecurrenceUnit.MONTH
                ? rangeStart.plusMonths(dto.period())
                : rangeStart.plusWeeks(dto.period());

        List<LocalDate> occurrences = new ArrayList<>();
        for (LocalDate cursor = rangeStart; cursor.isBefore(rangeEnd); cursor = cursor.plusDays(1)) {
            DayOfWeek day = DayOfWeek.valueOf(cursor.getDayOfWeek().name());
            if (selectedDays.contains(day)) {
                occurrences.add(cursor);
            }
        }
        if (occurrences.isEmpty()) {
            throw new BadRequestAlertException(
                    "No dates fall on the selected days in this period",
                    ENTITY_NAME,
                    "nodates"
            );
        }

        ZoneId zone = ZoneId.systemDefault();
        Instant from = rangeStart.atStartOfDay(zone).toInstant();
        Instant to = rangeEnd.atStartOfDay(zone).toInstant();
        Instant now = Instant.now();

        List<Appointment> resourceAppointments = appointmentRepository
                .findByFacilityIdAndDepartmentIdAndResourceTypeAndResourceIdAndStartDatetimeGreaterThanEqualAndStartDatetimeLessThanOrderByStartDatetimeAsc(
                        dto.facilityId(),
                        dto.departmentId(),
                        dto.requestedResourceType(),
                        dto.requestedResourceId(),
                        from,
                        to
                );
        List<Appointment> patientAppointments = appointmentRepository
                .findByFacilityIdAndPatient_IdAndStartDatetimeGreaterThanEqualAndStartDatetimeLessThan(
                        dto.facilityId(),
                        dto.patientId(),
                        from,
                        to
                );

        Set<LocalDate> occupiedDates = new HashSet<>();
        for (Appointment appointment : resourceAppointments) {
            if (occupiesDay(appointment) && appointment.getStartDatetime() != null) {
                occupiedDates.add(toLocalDate(appointment.getStartDatetime()));
            }
        }
        for (Appointment appointment : patientAppointments) {
            if (occupiesDay(appointment) && appointment.getStartDatetime() != null) {
                occupiedDates.add(toLocalDate(appointment.getStartDatetime()));
            }
        }

        List<Appointment> availableSlots = resourceAppointments.stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.NEW)
                .filter(appointment -> appointment.getStartDatetime() != null && !appointment.getStartDatetime().isBefore(now))
                .filter(appointment -> !occupiedDates.contains(toLocalDate(appointment.getStartDatetime())))
                .sorted(Comparator.comparing(Appointment::getStartDatetime))
                .toList();

        Map<LocalDate, Appointment> earliestSlotByDate = new LinkedHashMap<>();
        for (Appointment slot : availableSlots) {
            earliestSlotByDate.putIfAbsent(toLocalDate(slot.getStartDatetime()), slot);
        }

        List<MappedDay> days = new ArrayList<>();
        List<RecurringSkippedDayVM> skippedDays = new ArrayList<>();
        for (LocalDate date : occurrences) {
            DayOfWeek day = DayOfWeek.valueOf(date.getDayOfWeek().name());
            if (occupiedDates.contains(date)) {
                skippedDays.add(new RecurringSkippedDayVM(
                        date,
                        day,
                        "Appointment already exists on this day"
                ));
                continue;
            }
            days.add(new MappedDay(date, day, earliestSlotByDate.get(date)));
        }

        return new RecurringPlan(days, skippedDays, availableSlots);
    }

    private void validateRecurringPeriod(RecurringAppointmentRequestDTO dto) {
        if (dto.periodUnit() == RecurrenceUnit.WEEK && dto.period() > 52) {
            throw new BadRequestAlertException(
                    "Period cannot be more than 52 weeks",
                    ENTITY_NAME,
                    "periodlimit"
            );
        }
        if (dto.periodUnit() == RecurrenceUnit.MONTH && dto.period() > 24) {
            throw new BadRequestAlertException(
                    "Period cannot be more than 24 months",
                    ENTITY_NAME,
                    "periodlimit"
            );
        }
    }

    private List<Appointment> resolveSelectedSlots(List<Long> selectedAppointmentIds, RecurringPlan plan) {
        if (selectedAppointmentIds == null || selectedAppointmentIds.isEmpty()) {
            return plan.days().stream()
                    .map(MappedDay::slot)
                    .filter(Objects::nonNull)
                    .toList();
        }

        Map<Long, Appointment> availableById = new LinkedHashMap<>();
        for (Appointment slot : plan.availableSlots()) {
            if (slot.getId() != null) {
                availableById.putIfAbsent(slot.getId(), slot);
            }
        }

        Set<LocalDate> usedDates = new HashSet<>();
        List<Appointment> selected = new ArrayList<>();
        for (Long appointmentId : selectedAppointmentIds) {
            Appointment slot = availableById.get(appointmentId);
            if (slot == null || slot.getStartDatetime() == null) {
                continue;
            }
            if (usedDates.add(toLocalDate(slot.getStartDatetime()))) {
                selected.add(slot);
            }
        }
        return selected;
    }

    private RecurringAppointmentPreviewVM toPreview(RecurringPlan plan) {
        List<RecurringAppointmentDayVM> days = plan.days().stream()
                .map(day -> new RecurringAppointmentDayVM(
                        day.date(),
                        day.dayOfWeek(),
                        day.slot() != null ? day.slot().getId() : null,
                        day.slot() != null ? day.slot().getStartDatetime() : null,
                        day.slot() != null ? day.slot().getEndDatetime() : null,
                        day.slot() != null
                ))
                .toList();

        List<RecurringAvailableSlotVM> slots = plan.availableSlots().stream()
                .map(slot -> new RecurringAvailableSlotVM(
                        slot.getId(),
                        toLocalDate(slot.getStartDatetime()),
                        slot.getStartDatetime(),
                        slot.getEndDatetime()
                ))
                .toList();

        return new RecurringAppointmentPreviewVM(days, plan.skippedDays(), slots);
    }

    private boolean occupiesDay(Appointment appointment) {
        AppointmentStatus status = appointment.getStatus();
        return status != null
                && status != AppointmentStatus.NEW
                && status != AppointmentStatus.CANCELLED
                && status != AppointmentStatus.NO_SHOW;
    }

    private LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String formatRecurrenceDays(List<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            return null;
        }
        return days.stream()
                .filter(Objects::nonNull)
                .map(DayOfWeek::name)
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
    }

    private record MappedDay(LocalDate date, DayOfWeek dayOfWeek, Appointment slot) {
    }

    private record RecurringPlan(
            List<MappedDay> days,
            List<RecurringSkippedDayVM> skippedDays,
            List<Appointment> availableSlots
    ) {
    }

    private AppointmentRequest getRequest(Long id) {
        return appointmentRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Appointment request not found with id: " + id,
                        ENTITY_NAME,
                        "id.notfound"
                ));
    }

    private AppointmentRequestResponseVM toResponseVM(AppointmentRequest entity) {
        Long patientId = entity.getPatient() != null ? entity.getPatient().getId() : null;
        String patientName = entity.getPatient() != null ? entity.getPatient().getFirstName() + "" + entity.getPatient().getLastName() : null;
        String patientMrn = entity.getPatient() != null ? entity.getPatient().getMedicalRecordNumber() : null;
        Long sourceEncounterId = entity.getSourceEncounter() != null ? entity.getSourceEncounter().getId() : null;
        Long appointmentId = entity.getAppointment() != null ? entity.getAppointment().getId() : null;

        return new AppointmentRequestResponseVM(
                entity.getId(),
                patientId,
                patientName,
                patientMrn,
                entity.getFacilityId(),
                null,
                entity.getDepartmentId(),
                null,
                sourceEncounterId,
                appointmentId,
                entity.getRequestedResourceType(),
                entity.getRequestedResourceId(),
                entity.getPriority(),
                entity.getReason(),
                entity.getNote(),
                entity.getStatus(),
                entity.getCancelledAt(),
                entity.getCancelReason(),
                entity.getCreatedBy(),
                entity.getCreatedDate(),
                entity.getLastModifiedBy(),
                entity.getLastModifiedDate(),
                entity.getPreferredDate(),
                entity.getRecurring(),
                entity.getRecurrenceDays(),
                entity.getRecurrenceStartDate(),
                entity.getRecurrencePeriod(),
                entity.getRecurrenceUnit(),
                entity.getPreferredStartTime(),
                entity.getPreferredEndTime()
        );
    }
    private void notifyAppointmentRequestEvent(
            AppointmentRequest request,
            NotificationCode notificationCode,
            Map<String, Object> extraData
    ) {
        if (request == null || notificationCode == null) {
            return;
        }

        try {
            DepartmentDTO department = request.getDepartmentId() != null
                    ? departmentHelper.getDepartment(request.getDepartmentId())
                    : null;
            PractitionerDTO practitionerDTO = null;

            if (request.getRequestedResourceType() == TemplateType.PRACTITIONER
                    && request.getRequestedResourceId() != null) {

                practitionerDTO = practitionerHelper.getPractitioner(
                        request.getRequestedResourceId()
                );

            } else if (request.getRequestedResourceType() == TemplateType.DEPARTMENT
                    && request.getSourceEncounter() != null
                    && request.getSourceEncounter().getPractitionerId() != null) {

                practitionerDTO = practitionerHelper.getPractitioner(
                        request.getSourceEncounter().getPractitionerId()
                );
            }
            Map<String, Object> data =
                    buildAppointmentRequestNotificationData(request, department);

            if (extraData != null && !extraData.isEmpty()) {
                data.putAll(extraData);
            }

            String login = SecurityUtils.getCurrentUserLogin().orElse(null);


            Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule =
                    notificationHelper.resolveRecipients(
                            request.getDepartmentId(),
                            login,
                            request.getCreatedBy(),
                            request.getPatient(),
                            practitionerDTO,
                            false
                    );

            if (recipientsByRule.isEmpty()) {
                log.warn(
                        "[APPOINTMENT_REQUEST_NOTIFICATION] No recipients resolved. requestId={}, code={}",
                        request.getId(),
                        notificationCode
                );
                return;
            }

            log.debug(
                    "[APPOINTMENT_REQUEST_NOTIFICATION] Creating notification. requestId={}, code={}, recipientsByRule={}",
                    request.getId(),
                    notificationCode,
                    recipientsByRule
            );

            notificationHelper.sendNotification(
                    request.getFacilityId(),
                    notificationCode,
                    recipientsByRule,
                    data,
                    "APPOINTMENT_REQUEST",
                    request.getId()
            );

        } catch (Exception e) {

            log.warn(
                    "[APPOINTMENT_REQUEST_NOTIFICATION] Failed notification. requestId={}, code={}, error={}",
                    request.getId(),
                    notificationCode,
                    e.getMessage()
            );
        }
    }
    private Map<String, Object> buildAppointmentRequestNotificationData(
            AppointmentRequest request,
            DepartmentDTO department
    ) {
        Map<String, Object> data = new LinkedHashMap<>();

        FacilityDTO facilityDTO = null;

        if (request.getFacilityId() != null) {
            facilityDTO = facilityHelper.getFacility(request.getFacilityId());
        }

        Patient patient = request.getPatient();

        data.put(
                "facility_name",
                facilityDTO != null ? facilityDTO.name() : ""
        );

        data.put(
                "appointment_request_id",
                request.getId()
        );

        data.put(
                "request_id",
                request.getId()
        );

        data.put(
                "patient_id",
                patient != null ? patient.getId() : null
        );

        data.put(
                "patient_name",
                patient != null
                        ? notificationHelper.getPatientName(patient)
                        : ""
        );

        data.put(
                "patient_mrn",
                patient != null
                        ? patient.getMedicalRecordNumber()
                        : ""
        );

        data.put(
                "department_id",
                request.getDepartmentId()
        );

        data.put(
                "department_name",
                department != null ? department.name() : ""
        );

        data.put(
                "requested_resource_type",
                request.getRequestedResourceType() != null
                        ? request.getRequestedResourceType().name()
                        : ""
        );

        data.put(
                "requested_resource_id",
                request.getRequestedResourceId()
        );

        data.put(
                "priority",
                request.getPriority() != null
                        ? request.getPriority().name()
                        : ""
        );

        data.put(
                "reason",
                request.getReason() != null
                        ? request.getReason()
                        : ""
        );

        data.put(
                "note",
                request.getNote() != null
                        ? request.getNote()
                        : ""
        );

        data.put(
                "preferred_date",
                request.getPreferredDate() != null
                        ? request.getPreferredDate().toString()
                        : ""
        );
        data.put(
                "preferred_start_time",
                request.getPreferredStartTime() != null ? formatter.format(request.getPreferredStartTime()) : ""
        );
        data.put(
                "preferred_end_time",
                request.getPreferredEndTime() != null ? formatter.format(request.getPreferredEndTime()) : ""
        );

        data.put("recurring", Boolean.TRUE.equals(request.getRecurring()));
        data.put("recurrence_days", request.getRecurrenceDays() != null ? request.getRecurrenceDays() : "");
        data.put(
                "recurrence_start_date",
                request.getRecurrenceStartDate() != null ? request.getRecurrenceStartDate().toString() : ""
        );
        data.put("recurrence_period", request.getRecurrencePeriod());
        data.put(
                "recurrence_unit",
                request.getRecurrenceUnit() != null ? request.getRecurrenceUnit().name() : ""
        );

        data.put(
                "status",
                request.getStatus() != null
                        ? request.getStatus().name()
                        : ""
        );

        data.put(
                "appointment_id",
                request.getAppointment() != null
                        ? request.getAppointment().getId()
                        : null
        );
        data.put("appointment_date", request.getAppointment() != null && request.getAppointment().getStartDatetime() != null
                ? formatter.format(request.getAppointment().getStartDatetime())
                : "");

        return data;
    }
}