package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.OrganizationHolidayDTO;
import com.dazzle.asklepios.client.setup.dto.PolicyAssignmentDTO;
import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.AppointmentPolicyAssignment;
import com.dazzle.asklepios.domain.AvailabilityGenerationBatch;
import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.AvailabilityTemplateInterval;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BatchStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.HolidayHandlingMode;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.repository.AppointmentPolicyAssignmentRepository;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.repository.AvailabilityGenerationBatchRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateRepository;
import com.dazzle.asklepios.service.dto.availabilityGenerationBatch.AvailabilityGenerationBatchApplyDTO;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.service.helper.OrganizationHolidayHelper;
import com.dazzle.asklepios.service.helper.PolicyAssignmentHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.availabilityGenerationBatch.ApplyAvailabilityTemplateResponseVM;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class AvailabilityGenerationBatchService {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityGenerationBatchService.class);

    // fallback only - used when a facility has no timeZone configured. Each facility
    // can run in its own real-world zone (multi-region orgs), so the actual zone used
    // for generating slots is resolved per-template from its facility, not one global value.
    @Value("${patient.appointment.scheduling.zone}")
    private String defaultSchedulingZone;

    private ZoneId resolveZone(Long facilityId) {
        return facilityHelper.getFacilityZoneId(facilityId, defaultSchedulingZone);
    }

    private final AvailabilityTemplateRepository availabilityTemplateRepository;
    private final AvailabilityGenerationBatchRepository availabilityGenerationBatchRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentPolicyAssignmentRepository appointmentPolicyAssignmentRepository;
    private final OrganizationHolidayHelper organizationHolidayHelper;
    private final PolicyAssignmentHelper policyAssignmentHelper;
    private final FacilityHelper facilityHelper;

    @Transactional(readOnly = true)
    public Page<AvailabilityGenerationBatch> getListByParentTemplate(Long templateId, Pageable pageable) {
        AvailabilityTemplate parentTemplate = getTemplate(templateId);

        List<AvailabilityTemplate> childTemplates =
                availabilityTemplateRepository.findAllByParentTemplate_Id(templateId);

        List<Long> templateIds = new ArrayList<>();
        templateIds.add(parentTemplate.getId());
        templateIds.addAll(
                childTemplates.stream()
                        .map(AvailabilityTemplate::getId)
                        .toList()
        );

        return availabilityGenerationBatchRepository.findAllByTemplate_IdInOrderByApplyStartDateTimeDesc(templateIds, pageable);
    }

    @Transactional(readOnly = true)
    public AvailabilityTemplate getTemplate(Long templateId) {
        return availabilityTemplateRepository.findById(templateId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "idnotfound",
                        "availabilityTemplate",
                        "Availability template not found"
                ));
    }

    @Transactional(readOnly = true)
    public AvailabilityGenerationBatch getById(Long batchId) {
        LOG.debug("[GET_BY_ID] batchId={}", batchId);
        return availabilityGenerationBatchRepository.findById(batchId)
                .orElseThrow(() -> {
                    LOG.warn("[GET_BY_ID] AvailabilityGenerationBatch not found id={}", batchId);
                    return new BadRequestAlertException(
                            "id.notfound",
                            "AvailabilityGenerationBatch",
                            "AvailabilityGenerationBatch not found with id " + batchId
                    );
                });
    }

    public ApplyAvailabilityTemplateResponseVM applyTemplate(AvailabilityGenerationBatchApplyDTO request) {
        LOG.info("[APPLY TEMPLATE] templateId={}, startDate={}, endDate={}, deferred={}, deferredAt={}, holidayHandlingMode={}, policyAssignmentIds={}", request.templateId(), request.startDate(), request.endDate(), request.deferred(), request.deferredAt(), request.holidayHandlingMode(), request.policyAssignmentIds());

        AvailabilityTemplate template = getTemplate(request.templateId());
        validateTemplateForApply(template);

        ZoneId zone = resolveZone(template.getFacilityId());
        LocalDate startDate = request.startDate().atZone(zone).toLocalDate();
        LocalDate endDate = request.endDate().atZone(zone).toLocalDate();
        List<PolicyAssignmentDTO> activePolicyAssignments = validateAndGetActivePolicyAssignments(request.policyAssignmentIds());
        List<OrganizationHolidayDTO> holidays = organizationHolidayHelper.getOrganizationHolidayByDateRange(
                template.getFacilityId(),
                startDate,
                endDate
        );

        AvailabilityGenerationBatch batch = new AvailabilityGenerationBatch();
        batch.setTemplate(template);
        batch.setScope(request.scope());
        batch.setApplyStartDateTime(request.startDate());
        batch.setApplyEndDateTime(request.endDate());
        batch.setExecutionStatus(BatchStatus.PENDING);
        batch.setHolidayHandlingMode(request.holidayHandlingMode());
        batch = availabilityGenerationBatchRepository.save(batch);

        try {
            List<Appointment> generatedAppointments = generateAppointments(template, batch, request.startDate(), request.endDate(), request.deferred(), request.deferredAt(), request.holidayHandlingMode(), holidays, zone);

            List<Appointment> savedAppointments = appointmentRepository.saveAll(generatedAppointments);

            copyPolicyAssignmentsToAppointments(savedAppointments, activePolicyAssignments);

            int totalSlots = savedAppointments.size();
            int days = (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
            int dailyAvg = totalSlots == 0 || days <= 0 ? 0 : totalSlots / days;

            batch.setTotalSlots(totalSlots);
            batch.setDailyAvg(dailyAvg);
            batch.setExecutionStatus(BatchStatus.COMPLETED);
            availabilityGenerationBatchRepository.save(batch);

            return new ApplyAvailabilityTemplateResponseVM(
                    batch.getId(),
                    template.getId(),
                    batch.getScope(),
                    batch.getApplyStartDateTime(),
                    batch.getApplyEndDateTime(),
                    totalSlots,
                    dailyAvg,
                    batch.getExecutionStatus(),
                    buildApplyMessage(request.holidayHandlingMode()),
                    batch.getHolidayHandlingMode()
            );
        } catch (Exception ex) {
            LOG.error("[APPLY TEMPLATE FAILED] templateId={}, error={}", template.getId(), ex.getMessage(), ex);

            batch.setExecutionStatus(BatchStatus.FAILED);
            availabilityGenerationBatchRepository.save(batch);

            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<AvailabilityGenerationBatch> getListByTemplateExcludingBatch(
            Long templateId,
            Long batchId,
            Pageable pageable
    ) {
        return availabilityGenerationBatchRepository
                .findAllByTemplate_IdAndIdNotOrderByApplyStartDateTimeDesc(templateId, batchId, pageable);
    }

    private List<Appointment> generateAppointments(AvailabilityTemplate template, AvailabilityGenerationBatch batch, Instant startDate, Instant endDate, boolean deferred, Instant deferredAt, HolidayHandlingMode holidayHandlingMode, List<OrganizationHolidayDTO> holidays, ZoneId zone) {
        LOG.info("[GENERATE TEMPLATE] templateId={}, batchId={}, startDate={}, endDate={}, deferred={}, deferredAt={}, holidayHandlingMode={}", template.getId(), batch.getId(), startDate, endDate, deferred, deferredAt, holidayHandlingMode);

        List<Appointment> appointments = new ArrayList<>();
        Instant current = startDate;
        while (!current.isAfter(endDate)) {
            LocalDate currentDate = current.atZone(zone).toLocalDate();
            boolean holiday = isHoliday(currentDate, holidays);

            if (holiday && holidayHandlingMode == HolidayHandlingMode.EXCLUDE_HOLIDAYS) {
                current = current.plus(1, ChronoUnit.DAYS);
                continue;
            }

            for (AvailabilityTemplateInterval interval : template.getIntervals()) {
                if (!matchesDay(currentDate, interval)) {
                    continue;
                }
                appointments.addAll(
                        generateAppointmentsForInterval(
                                template,
                                batch,
                                current,
                                startDate,
                                endDate,
                                interval,
                                deferred,
                                deferredAt,
                                zone
                        )
                );
            }
            current = current.plus(1, ChronoUnit.DAYS);
        }

        return appointments;
    }

    private List<Appointment> generateAppointmentsForInterval(AvailabilityTemplate template, AvailabilityGenerationBatch batch, Instant currentDate, Instant applyStart, Instant applyEnd, AvailabilityTemplateInterval interval, boolean deferred, Instant deferredAt, ZoneId zone) {
        List<Appointment> appointments = new ArrayList<>();

        int slotDuration = interval.getSlotDurationMinutes() != null
                ? interval.getSlotDurationMinutes()
                : template.getDurationMinutes();

        int slotBeforeMinutes = template.getDefaultBufferBeforeMinutes() != null
                ? template.getDefaultBufferBeforeMinutes()
                : 0;

        int slotAfterMinutes = template.getDefaultBufferAfterMinutes() != null
                ? template.getDefaultBufferAfterMinutes()
                : 0;

        int parallelCapacity = template.getParallelCapacityValue() != null
                && template.getParallelCapacityValue() > 0
                ? template.getParallelCapacityValue()
                : 1;

        LocalDate currentLocalDate = LocalDateTime.ofInstant(currentDate, zone).toLocalDate();
        LocalDateTime applyStartDateTime = LocalDateTime.ofInstant(applyStart, zone);
        LocalDateTime applyEndDateTime = LocalDateTime.ofInstant(applyEnd, zone);

        LocalDateTime intervalStart = LocalDateTime.of(currentLocalDate, interval.getStartTime());
        LocalDateTime intervalEnd = LocalDateTime.of(currentLocalDate, interval.getEndTime());

        LocalDateTime effectiveStart = intervalStart;
        LocalDateTime effectiveEnd = intervalEnd;

        if (currentLocalDate.equals(applyStartDateTime.toLocalDate()) && applyStartDateTime.isAfter(effectiveStart)) {
            effectiveStart = applyStartDateTime;
        }

        if (currentLocalDate.equals(applyEndDateTime.toLocalDate()) && applyEndDateTime.isBefore(effectiveEnd)) {
            effectiveEnd = applyEndDateTime;
        }

        if (!effectiveStart.isBefore(effectiveEnd)) {
            return appointments;
        }

        LocalDateTime currentSlotStart = effectiveStart;

        while (true) {
            LocalDateTime beforeBufferStart = currentSlotStart.minusMinutes(slotBeforeMinutes);
            LocalDateTime beforeBufferEnd = currentSlotStart;

            LocalDateTime slotStart = currentSlotStart;
            LocalDateTime slotEnd = slotStart.plusMinutes(slotDuration);

            LocalDateTime afterBufferStart = slotEnd;
            LocalDateTime afterBufferEnd = slotEnd.plusMinutes(slotAfterMinutes);

            if (slotEnd.isAfter(effectiveEnd)) {
                break;
            }

            LocalDateTime overlappingBreakEnd =
                    findOverlappingBreakEnd(interval, currentLocalDate, slotStart, slotEnd);

            if (overlappingBreakEnd != null) {
                currentSlotStart = overlappingBreakEnd.plusMinutes(slotBeforeMinutes);
                continue;
            }

            for (int i = 0; i < parallelCapacity; i++) {
                if (slotBeforeMinutes > 0) {
                    appointments.add(buildAppointment(
                            template, batch, deferred, deferredAt, i + 1,
                            beforeBufferStart, beforeBufferEnd, BookingMode.BUFFER, zone
                    ));
                }

                appointments.add(buildAppointment(
                        template, batch, deferred, deferredAt, i + 1,
                        slotStart, slotEnd, BookingMode.SLOT, zone
                ));

                if (slotAfterMinutes > 0) {
                    appointments.add(buildAppointment(
                            template, batch, deferred, deferredAt, i + 1,
                            afterBufferStart, afterBufferEnd, BookingMode.BUFFER, zone
                    ));
                }
            }

            currentSlotStart = afterBufferEnd.plusMinutes(slotBeforeMinutes);
        }

        return appointments;
    }

    private Appointment buildAppointment(AvailabilityTemplate template, AvailabilityGenerationBatch batch, boolean deferred, Instant deferredAt, int capacityIndex, LocalDateTime start, LocalDateTime end, BookingMode bookingMode, ZoneId zone) {
        Appointment appointment = new Appointment();
        appointment.setFacilityId(template.getFacilityId());
        appointment.setDepartmentId(template.getDepartmentId());
        appointment.setAvailabilityGenerationBatch(batch);
        appointment.setResourceType(template.getTemplateType());
        appointment.setResourceId(template.getResourceId());
        appointment.setStartDatetime(toInstant(start, zone));
        appointment.setEndDatetime(toInstant(end, zone));
        appointment.setPatient(null);
        appointment.setDefaultServiceId(template.getDefaultServiceId());
        appointment.setDefaultPractitionerId(template.getDefaultPractitionerId());
        appointment.setBookingMode(bookingMode);
        appointment.setStatus(AppointmentStatus.NEW);
        appointment.setDeferred(deferred);
        appointment.setDeferredAt(deferredAt);
        appointment.setRequireConfirmation(
                template.getRequireConfirmation() != null ? template.getRequireConfirmation() : true
        );
        appointment.setRequirePractitioner(
                template.getRequirePractitioner() != null ? template.getRequirePractitioner() : false
        );
        appointment.setPriority(EncounterPriority.NORMAL);
        appointment.setCapacityIndex(capacityIndex);
        appointment.setReason(null);
        appointment.setHl7AppointmentNumber(null);
        return appointment;
    }

    private LocalDateTime findOverlappingBreakEnd(AvailabilityTemplateInterval interval, LocalDate date, LocalDateTime slotStart, LocalDateTime slotEnd) {
        if (interval.getBreaks() == null || interval.getBreaks().isEmpty()) {
            return null;
        }

        return interval.getBreaks().stream()
                .map(intervalBreak -> new Object() {
                    final LocalDateTime breakStart = LocalDateTime.of(date, intervalBreak.getStartTime());
                    final LocalDateTime breakEnd = LocalDateTime.of(date, intervalBreak.getEndTime());
                })
                .filter(b -> slotStart.isBefore(b.breakEnd) && slotEnd.isAfter(b.breakStart))
                .map(b -> b.breakEnd)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private boolean matchesDay(LocalDate date, AvailabilityTemplateInterval interval) {
        String currentDay = date.getDayOfWeek().name();
        return interval.getDayOfWeek().name().equalsIgnoreCase(currentDay);
    }

    private void validateTemplateForApply(AvailabilityTemplate template) {
        if (!Boolean.TRUE.equals(template.getIsActive())) {
            throw new BadRequestAlertException("templateinactive", "availabilityTemplate", "Template is inactive");
        }

        if (template.getStatus() != TemplateStatus.PUBLISHED) {
            throw new BadRequestAlertException("templatenotpublished", "availabilityTemplate", "Only published template can be applied");
        }

        if (template.getIntervals() == null || template.getIntervals().isEmpty()) {
            throw new BadRequestAlertException("templatenointervals", "availabilityTemplate", "Template has no intervals");
        }
    }

    private String buildApplyMessage(HolidayHandlingMode holidayHandlingMode) {
        if (holidayHandlingMode == HolidayHandlingMode.INCLUDE_AS_EXCEPTION) {
            return "Template applied successfully including organization holidays as exceptions";
        }
        return "Template applied successfully excluding organization holidays";
    }

    private Instant toInstant(LocalDateTime dateTime, ZoneId zone) {
        return dateTime.atZone(zone).toInstant();
    }

    private boolean isHoliday(LocalDate date, List<OrganizationHolidayDTO> holidays) {
        LOG.info("[isHoliday] date={}, holidays={}", date, holidays);

        if (date == null || holidays == null || holidays.isEmpty()) {
            return false;
        }

        return holidays.stream().anyMatch(holiday -> holiday.startDate() != null
                && holiday.endDate() != null
                && !date.isBefore(holiday.startDate())
                && !date.isAfter(holiday.endDate())
        );
    }

    private List<PolicyAssignmentDTO> validateAndGetActivePolicyAssignments(List<Long> policyAssignmentIds) {
        if (policyAssignmentIds == null || policyAssignmentIds.isEmpty()) {
            return List.of();
        }

        List<Long> distinctIds = policyAssignmentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (distinctIds.isEmpty()) {
            return List.of();
        }

        List<PolicyAssignmentDTO> policyAssignments = new ArrayList<>();

        for (Long policyAssignmentId : distinctIds) {
            PolicyAssignmentDTO policyAssignment = policyAssignmentHelper.getPolicyAssignment(policyAssignmentId);
            policyAssignments.add(policyAssignment);
        }

        return policyAssignments.stream()
                .filter(policyAssignment -> Boolean.TRUE.equals(policyAssignment.isActive()))
                .toList();
    }

    private void copyPolicyAssignmentsToAppointments(List<Appointment> appointments, List<PolicyAssignmentDTO> activePolicyAssignments) {
        if (appointments == null || appointments.isEmpty()) {
            return;
        }

        if (activePolicyAssignments == null || activePolicyAssignments.isEmpty()) {
            return;
        }

        List<AppointmentPolicyAssignment> appointmentPolicyAssignments = new ArrayList<>();

        for (Appointment appointment : appointments) {
            for (PolicyAssignmentDTO policyAssignment : activePolicyAssignments) {
                AppointmentPolicyAssignment appointmentPolicyAssignment = new AppointmentPolicyAssignment();

                appointmentPolicyAssignment.setAppointment(appointment);
                appointmentPolicyAssignment.setPolicyAssignmentId(policyAssignment.id());
                appointmentPolicyAssignment.setPolicyId(policyAssignment.policy().id());
                appointmentPolicyAssignment.setIsRequired(Boolean.TRUE.equals(policyAssignment.isRequired()));
                appointmentPolicyAssignment.setIsApplied(false);

                appointmentPolicyAssignments.add(appointmentPolicyAssignment);
            }
        }

        appointmentPolicyAssignmentRepository.saveAll(appointmentPolicyAssignments);

        LOG.info("[COPY POLICY ASSIGNMENTS] appointmentsCount={}, activePolicyAssignmentsCount={}, insertedRows={}", appointments.size(), activePolicyAssignments.size(), appointmentPolicyAssignments.size());
    }
}