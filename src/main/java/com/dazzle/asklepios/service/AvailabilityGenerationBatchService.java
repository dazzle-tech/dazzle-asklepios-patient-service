package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.OrganizationHolidayDTO;
import com.dazzle.asklepios.domain.AppointmentFromTemplate;
import com.dazzle.asklepios.domain.AvailabilityGenerationBatch;
import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.AvailabilityTemplateInterval;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BatchStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
import com.dazzle.asklepios.domain.enumeration.EncounterPriority;
import com.dazzle.asklepios.domain.enumeration.HolidayHandlingMode;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.repository.AppointmentFromTemplateRepository;
import com.dazzle.asklepios.repository.AvailabilityGenerationBatchRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateRepository;
import com.dazzle.asklepios.service.dto.availabilityGenerationBatch.AvailabilityGenerationBatchApplyDTO;
import com.dazzle.asklepios.service.helper.OrganizationHolidayHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.availabilityGenerationBatch.ApplyAvailabilityTemplateResponseVM;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Service
@RequiredArgsConstructor
@Transactional
public class AvailabilityGenerationBatchService {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityGenerationBatchService.class);

    private final AvailabilityTemplateRepository availabilityTemplateRepository;
    private final AvailabilityGenerationBatchRepository availabilityGenerationBatchRepository;
    private final AppointmentFromTemplateRepository appointmentFromTemplateRepository;
    private final OrganizationHolidayHelper organizationHolidayHelper;

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
        LOG.info("[APPLY TEMPLATE] templateId={}, startDate={}, endDate={}, deferred={}, deferredAt={}, holidayHandlingMode={}",
                request.templateId(),
                request.startDate(),
                request.endDate(),
                request.deferred(),
                request.deferredAt(),
                request.holidayHandlingMode());


        AvailabilityTemplate template = getTemplate(request.templateId());
        validateTemplateForApply(template);

        LocalDate startDate = request.startDate().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = request.endDate().atZone(ZoneId.systemDefault()).toLocalDate();

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
            List<AppointmentFromTemplate> generatedAppointments = generateAppointments(template, batch, request.startDate(), request.endDate(), request.deferred(), request.deferredAt(), request.holidayHandlingMode(), holidays);

            appointmentFromTemplateRepository.saveAll(generatedAppointments);

            int totalSlots = generatedAppointments.size();
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

    private List<AppointmentFromTemplate> generateAppointments(AvailabilityTemplate template, AvailabilityGenerationBatch batch, Instant startDate, Instant endDate, boolean deferred, Instant deferredAt, HolidayHandlingMode holidayHandlingMode, List<OrganizationHolidayDTO> holidays) {
        LOG.info("[GENERATE TEMPLATE] templateId={}, batchId={},startDate={}, endDate={}, deferred={}, deferredAt={}, holidayHandlingMode={}", template.getId(), batch.getId(), startDate, endDate, deferred, deferredAt, holidayHandlingMode);


        List<AppointmentFromTemplate> appointments = new ArrayList<>();
        ZoneId zone = ZoneId.systemDefault();
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
                                deferredAt
                        )
                );
            }
            current = current.plus(1, ChronoUnit.DAYS);
        }

        return appointments;
    }

    private List<AppointmentFromTemplate> generateAppointmentsForInterval(
            AvailabilityTemplate template,
            AvailabilityGenerationBatch batch,
            Instant currentDate,
            Instant applyStart,
            Instant applyEnd,
            AvailabilityTemplateInterval interval,
            boolean deferred,
            Instant deferredAt
    ) {
        List<AppointmentFromTemplate> appointments = new ArrayList<>();

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

        ZoneId zone = ZoneId.systemDefault();

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
                            beforeBufferStart, beforeBufferEnd, BookingMode.BUFFER
                    ));
                }

                appointments.add(buildAppointment(
                        template, batch, deferred, deferredAt, i + 1,
                        slotStart, slotEnd, BookingMode.SLOT
                ));

                if (slotAfterMinutes > 0) {
                    appointments.add(buildAppointment(
                            template, batch, deferred, deferredAt, i + 1,
                            afterBufferStart, afterBufferEnd, BookingMode.BUFFER
                    ));
                }
            }

            currentSlotStart = afterBufferEnd.plusMinutes(slotBeforeMinutes);
        }

        return appointments;
    }

    private AppointmentFromTemplate buildAppointment(AvailabilityTemplate template, AvailabilityGenerationBatch batch, boolean deferred, Instant deferredAt, int capacityIndex, LocalDateTime start, LocalDateTime end, BookingMode bookingMode) {
        AppointmentFromTemplate appointment = new AppointmentFromTemplate();
        appointment.setFacilityId(template.getFacilityId());
        appointment.setDepartmentId(template.getDepartmentId());
        appointment.setAvailabilityGenerationBatch(batch);
        appointment.setResourceType(template.getTemplateType());
        appointment.setResourceId(template.getResourceId());
        appointment.setStartDatetime(toInstant(start));
        appointment.setEndDatetime(toInstant(end));
        appointment.setPatient(null);
        appointment.setDefaultServiceId(template.getDefaultServiceId());
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

    private Instant toInstant(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant();
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
}
