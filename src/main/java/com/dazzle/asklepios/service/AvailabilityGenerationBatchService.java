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
                .orElseThrow(() -> new NotFoundAlertException(
                        "Availability template not found",
                        "availabilityTemplate",
                        "idnotfound"
                ));
    }
    @Transactional(readOnly = true)
    public AvailabilityGenerationBatch getById(Long batchId) {
        LOG.debug("[GET_BY_ID] batchId={}", batchId);
        return availabilityGenerationBatchRepository.findById(batchId)
                .orElseThrow(() -> {
                    LOG.warn("[GET_BY_ID] AvailabilityGenerationBatch not found id={}", batchId);
                    return new NotFoundAlertException(
                            "AvailabilityGenerationBatch not found with id " + batchId,
                            "AvailabilityGenerationBatch",
                            "id.notfound"
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

        List<OrganizationHolidayDTO> holidays = organizationHolidayHelper.getOrganizationHolidayByDateRange(
                template.getFacilityId(),
                request.startDate(),
                request.endDate()
        );

        AvailabilityGenerationBatch batch = new AvailabilityGenerationBatch();
        batch.setTemplate(template);
        batch.setScope(request.scope());
        batch.setApplyStartDateTime(toStartOfDayInstant(request.startDate()));
        batch.setApplyEndDateTime(toEndOfDayInstant(request.endDate()));
        batch.setExecutionStatus(BatchStatus.PENDING);
        batch.setHolidayHandlingMode(request.holidayHandlingMode());
        batch = availabilityGenerationBatchRepository.save(batch);

        try {
            List<AppointmentFromTemplate> generatedAppointments = generateAppointments(template, batch, request.startDate(), request.endDate(), request.deferred(), request.deferredAt(), request.holidayHandlingMode(), holidays);

            appointmentFromTemplateRepository.saveAll(generatedAppointments);

            int totalSlots = generatedAppointments.size();
            int days = (int) (request.endDate().toEpochDay() - request.startDate().toEpochDay()) + 1;
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

    private List<AppointmentFromTemplate> generateAppointments(
            AvailabilityTemplate template,
            AvailabilityGenerationBatch batch,
            LocalDate startDate,
            LocalDate endDate,
            boolean deferred,
            Instant deferredAt,
            HolidayHandlingMode holidayHandlingMode,
            List<OrganizationHolidayDTO> holidays
    ) {
        LOG.info("[GENERATE TEMPLATE] templateId={}, batchId={},startDate={}, endDate={}, deferred={}, deferredAt={}, holidayHandlingMode={}",
                template.getId(),
                batch.getId(),
                startDate,
                endDate,
                deferred,
                deferredAt,
                holidayHandlingMode);


        List<AppointmentFromTemplate> appointments = new ArrayList<>();

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {

            boolean holiday = isHoliday(current, holidays);

            if (holiday && holidayHandlingMode == HolidayHandlingMode.EXCLUDE_HOLIDAYS) {
                current = current.plusDays(1);
                continue;
            }

            for (AvailabilityTemplateInterval interval : template.getIntervals()) {
                if (!matchesDay(current, interval)) {
                    continue;
                }

                appointments.addAll(generateAppointmentsForInterval(
                        template,
                        batch,
                        current,
                        interval,
                        deferred,
                        deferredAt,
                        holiday,
                        holidayHandlingMode
                ));
            }

            current = current.plusDays(1);
        }

        return appointments;
    }

    private List<AppointmentFromTemplate> generateAppointmentsForInterval(
            AvailabilityTemplate template,
            AvailabilityGenerationBatch batch,
            LocalDate date,
            AvailabilityTemplateInterval interval,
            boolean deferred,
            Instant deferredAt,
            boolean holiday,
            HolidayHandlingMode holidayHandlingMode
    ) {

        LOG.info("[GENERATE APPOINTMENT FOR INTERVAL] templateId={}, batchId={},date={}, interval={}, deferred={}, deferredAt={}, holidayHandlingMode={}",
                template.getId(),
                batch.getId(),
                date,
                interval,
                deferred,
                deferredAt,
                holidayHandlingMode);


        List<AppointmentFromTemplate> appointments = new ArrayList<>();

        int slotDuration = interval.getSlotDurationMinutes() != null
                ? interval.getSlotDurationMinutes()
                : template.getDurationMinutes();

        int parallelCapacity = template.getParallelCapacityValue() != null
                && template.getParallelCapacityValue() > 0
                ? template.getParallelCapacityValue()
                : 1;

        LocalDateTime slotStart = LocalDateTime.of(date, interval.getStartTime());
        LocalDateTime intervalEnd = LocalDateTime.of(date, interval.getEndTime());

        while (!slotStart.plusMinutes(slotDuration).isAfter(intervalEnd)) {
            LocalDateTime slotEnd = slotStart.plusMinutes(slotDuration);

            for (int i = 0; i < parallelCapacity; i++) {
                AppointmentFromTemplate appointment = new AppointmentFromTemplate();
                appointment.setFacilityId(template.getFacilityId());
                appointment.setDepartmentId(template.getDepartmentId());
                appointment.setAvailabilityGenerationBatch(batch);
                appointment.setResourceType(template.getTemplateType());
                appointment.setResourceId(template.getResourceId());
                appointment.setStartDatetime(toInstant(slotStart));
                appointment.setEndDatetime(toInstant(slotEnd));
                appointment.setPatient(null);
                appointment.setDefaultServiceId(template.getDefaultServiceId());
                appointment.setDefaultPractitionerId(template.getDefaultPractitionerId());
                appointment.setBookingMode(BookingMode.SLOT);
                appointment.setStatus(AppointmentStatus.NEW);
                appointment.setDeferred(deferred);
                appointment.setDeferredAt(deferredAt);
                appointment.setPriority(EncounterPriority.NORMAL);
                appointment.setCapacityIndex(i + 1);

                appointment.setReason(null);

                appointments.add(appointment);
            }

            slotStart = slotEnd;
        }

        return appointments;
    }

    private boolean matchesDay(LocalDate date, AvailabilityTemplateInterval interval) {
        String currentDay = date.getDayOfWeek().name();
        return interval.getDayOfWeek().name().equalsIgnoreCase(currentDay);
    }

    private void validateTemplateForApply(AvailabilityTemplate template) {
        if (!Boolean.TRUE.equals(template.getIsActive())) {
            throw new BadRequestAlertException("Template is inactive", "availabilityTemplate", "templateinactive");
        }

        if (template.getStatus() != TemplateStatus.PUBLISHED) {
            throw new BadRequestAlertException("Only published template can be applied", "availabilityTemplate", "templatenotpublished");
        }

        if (template.getIntervals() == null || template.getIntervals().isEmpty()) {
            throw new BadRequestAlertException("Template has no intervals", "availabilityTemplate", "templatenointervals");
        }
    }

    private String buildApplyMessage(HolidayHandlingMode holidayHandlingMode) {
        if (holidayHandlingMode == HolidayHandlingMode.INCLUDE_AS_EXCEPTION) {
            return "Template applied successfully including organization holidays as exceptions";
        }
        return "Template applied successfully excluding organization holidays";
    }

    private Instant toStartOfDayInstant(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private Instant toEndOfDayInstant(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusNanos(1).toInstant();
    }

    private Instant toInstant(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant();
    }

    private boolean isHoliday(LocalDate date, List<OrganizationHolidayDTO> holidays) {
        LOG.info("[isHoliday] date={}, holidays={}",
                date,
                holidays);

        if (date == null || holidays == null || holidays.isEmpty()) {
            return false;
        }

        return holidays.stream().anyMatch(holiday ->
                holiday.startDate() != null
                        && holiday.endDate() != null
                        && !date.isBefore(holiday.startDate())
                        && !date.isAfter(holiday.endDate())
        );
    }
}