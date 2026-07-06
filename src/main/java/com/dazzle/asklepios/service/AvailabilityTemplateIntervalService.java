package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.AvailabilityTemplateAllowedService;
import com.dazzle.asklepios.domain.AvailabilityTemplateInterval;
import com.dazzle.asklepios.domain.AvailabilityTemplateIntervalBreak;
import com.dazzle.asklepios.domain.enumeration.DayOfWeek;
import com.dazzle.asklepios.repository.AvailabilityTemplateAllowedServiceRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateIntervalBreakRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateIntervalRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateRepository;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateAllowedServices.AvailabilityTemplateAllowedServiceDTO;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateInterval.AvailabilityTemplateIntervalCreateDTO;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateInterval.AvailabilityTemplateIntervalUpdateDTO;
import com.dazzle.asklepios.service.dto.workingDays.WorkingDayJson;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class AvailabilityTemplateIntervalService {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityTemplateIntervalService.class);

    private final AvailabilityTemplateIntervalRepository intervalRepository;
    private final AvailabilityTemplateAllowedServiceRepository allowedServiceRepository;
    private final AvailabilityTemplateRepository templateRepository;
    private final AvailabilityTemplateIntervalBreakRepository availabilityTemplateIntervalBreakRepository;

    public AvailabilityTemplateIntervalService(AvailabilityTemplateIntervalRepository intervalRepository, AvailabilityTemplateAllowedServiceRepository allowedServiceRepository, AvailabilityTemplateRepository templateRepository, AvailabilityTemplateIntervalBreakRepository availabilityTemplateIntervalBreakRepository) {
        this.intervalRepository = intervalRepository;
        this.allowedServiceRepository = allowedServiceRepository;
        this.templateRepository = templateRepository;
        this.availabilityTemplateIntervalBreakRepository = availabilityTemplateIntervalBreakRepository;
    }

    public AvailabilityTemplateInterval create(AvailabilityTemplateIntervalCreateDTO dto) {
        LOG.debug("Request to create AvailabilityTemplateInterval: {}", dto);

        if (dto == null) {
            throw new BadRequestAlertException(
                    "payload.required",
                    "availabilityTemplateInterval",
                    "Interval payload is required");
        }

        AvailabilityTemplate template = templateRepository.findById(dto.templateId())
                .orElseThrow(() ->
                        new BadRequestAlertException(
                                "template.notfound",
                                "availabilityTemplateInterval",
                                "Template not found with id " + dto.templateId()));

        validateInterval(
                dto.dayOfWeek(),
                dto.startTime(),
                dto.endTime(),
                dto.slotDurationMinutes());

        validateNoOverlap(
                template.getId(),
                dto.dayOfWeek(),
                dto.startTime(),
                dto.endTime());

        return createIntervalForDay(template, dto, dto.dayOfWeek())
                .orElseThrow(() ->
                        new BadRequestAlertException(
                                "create.failed",
                                "availabilityTemplateInterval",
                                "Failed to create availability template interval"));
    }

    public Optional<AvailabilityTemplateInterval> update(Long id, AvailabilityTemplateIntervalUpdateDTO dto) {
        LOG.debug("Request to update AvailabilityTemplateInterval id={} with {}", id, dto);

        AvailabilityTemplateInterval interval = intervalRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException("notfound", "availabilityTemplateInterval","Interval not found with id " + id));

        AvailabilityTemplate template = interval.getTemplate();

        if (dto.dayOfWeek() != null) {
            interval.setDayOfWeek(dto.dayOfWeek());
        }
        if (dto.startTime() != null) {
            interval.setStartTime(dto.startTime());
        }
        if (dto.endTime() != null) {
            interval.setEndTime(dto.endTime());
        }
        if (dto.slotStrategy() != null) {
            interval.setSlotStrategy(dto.slotStrategy());
        }
        if (dto.slotDurationMinutes() != null) {
            interval.setSlotDurationMinutes(dto.slotDurationMinutes());
        }

        validateInterval(interval.getDayOfWeek(), interval.getStartTime(), interval.getEndTime(), interval.getSlotDurationMinutes());
        validateNoOverlapForUpdate(interval.getId(), template.getId(), interval.getDayOfWeek(), interval.getStartTime(), interval.getEndTime());

        AvailabilityTemplateInterval savedInterval = intervalRepository.save(interval);

        if (dto.allowedServices() != null) {
            List<AvailabilityTemplateAllowedService> savedAllowedServices = replaceAllowedServices(savedInterval, template, dto.allowedServices());
            savedInterval.setAllowedServices(savedAllowedServices);
        }

        return Optional.of(savedInterval);
    }

    public void hardDelete(Long id) {
        LOG.debug("Request to hard delete AvailabilityTemplateInterval id={}", id);

        if (!intervalRepository.existsById(id)) {
            throw new BadRequestAlertException("Interval not found with id " + id, "availabilityTemplateInterval", "notfound");
        }

        allowedServiceRepository.deleteByInterval_Id(id);
        availabilityTemplateIntervalBreakRepository.deleteByInterval_Id(id);
        intervalRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityTemplateInterval> findByTemplateAndDayOfWeek(Long templateId, DayOfWeek dayOfWeek) {
        LOG.debug("Request to get AvailabilityTemplateIntervals by templateId={} and dayOfWeek={}", templateId, dayOfWeek);

        if (templateId == null) {
            throw new BadRequestAlertException("Template id is required", "availabilityTemplateInterval", "template.required");
        }

        if (dayOfWeek == null) {
            throw new BadRequestAlertException("Day of week is required", "availabilityTemplateInterval", "dayofweek.required");
        }

        return intervalRepository.findByTemplate_IdAndDayOfWeek(templateId, dayOfWeek);
    }

    @Transactional(readOnly = true)
    public Optional<AvailabilityTemplateInterval> findOne(Long id) {
        LOG.debug("Request to get AvailabilityTemplateInterval id={}", id);
        return intervalRepository.findWithAllowedServicesById(id);
    }

    private List<AvailabilityTemplateAllowedService> replaceAllowedServices(AvailabilityTemplateInterval interval, AvailabilityTemplate template, List<AvailabilityTemplateAllowedServiceDTO> allowedServices) {
        allowedServiceRepository.deleteByInterval_Id(interval.getId());
        allowedServiceRepository.flush();

        if (allowedServices == null || allowedServices.isEmpty()) {
            return List.of();
        }

        List<AvailabilityTemplateAllowedService> entities = allowedServices.stream()
                .filter(Objects::nonNull)
                .filter(dto -> dto.service() != null)
                .map(dto -> {
                    AvailabilityTemplateAllowedService entity = new AvailabilityTemplateAllowedService();
                    entity.setTemplate(template);
                    entity.setInterval(interval);
                    entity.setDayOfWeek(interval.getDayOfWeek());
                    entity.setService(dto.service());
                    return entity;
                })
                .toList();

        return allowedServiceRepository.saveAllAndFlush(entities);
    }

    private void validateInterval(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,Integer slotDurationMinutes)
    {
        if (dayOfWeek == null) {
            throw new BadRequestAlertException("dayofweek.required", "availabilityTemplateInterval","Day of week is required" );
        }

        if (startTime == null) {
            throw new BadRequestAlertException("starttime.required", "availabilityTemplateInterval", "Start time is required");
        }

        if (endTime == null) {
            throw new BadRequestAlertException("endtime.required", "availabilityTemplateInterval","End time is required");
        }

        if (!endTime.isAfter(startTime)) {
            throw new BadRequestAlertException("timerange.invalid", "availabilityTemplateInterval","End time must be after start time");
        }

        if (slotDurationMinutes == null || slotDurationMinutes <= 0) {
            throw new BadRequestAlertException("slotduration.invalid", "availabilityTemplateInterval", "Slot duration minutes must be greater than 0");
        }
    }
    private Optional<AvailabilityTemplateInterval> createIntervalForDay(AvailabilityTemplate template, AvailabilityTemplateIntervalCreateDTO dto, DayOfWeek dayOfWeek) {
        AvailabilityTemplateInterval interval = new AvailabilityTemplateInterval();
        interval.setTemplate(template);
        interval.setDayOfWeek(dayOfWeek);
        interval.setStartTime(dto.startTime());
        interval.setEndTime(dto.endTime());
        interval.setSlotStrategy(dto.slotStrategy());
        interval.setSlotDurationMinutes(dto.slotDurationMinutes());

        AvailabilityTemplateInterval savedInterval = intervalRepository.save(interval);
        List<AvailabilityTemplateAllowedService> savedAllowedServices = replaceAllowedServices(savedInterval, template, dto.allowedServices());
        savedInterval.setAllowedServices(savedAllowedServices);
        return Optional.of(savedInterval);
    }

private List<DayOfWeek> resolveWorkingDays(AvailabilityTemplate template) {

    List<WorkingDayJson> workingDays = template.getWorkingDays();

    if (workingDays == null || workingDays.isEmpty()) {
        return List.of();
    }

    Set<DayOfWeek> targetDays = new LinkedHashSet<>();

    workingDays.stream()
            .filter(Objects::nonNull)
            .filter(day -> Boolean.TRUE.equals(day.getIsWorking()))
            .map(WorkingDayJson::getDayOfWeek)
            .filter(Objects::nonNull)
            .forEach(targetDays::add);

    return new ArrayList<>(targetDays);
}
    private boolean hasOverlap(Long templateId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        return !intervalRepository.findByTemplate_IdAndDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThan(templateId, dayOfWeek, endTime, startTime).isEmpty();
    }

    private void validateNoOverlap(Long templateId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime ) {
        if (hasOverlap(templateId, dayOfWeek, startTime, endTime)) {
            throw new BadRequestAlertException("interval.overlap", "availabilityTemplateInterval", "Interval overlaps with an existing interval for the same template and day");
        }
    }

    private void validateNoOverlapForUpdate(Long intervalId, Long templateId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        List<AvailabilityTemplateInterval> overlaps =
                intervalRepository.findByTemplate_IdAndDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(templateId, dayOfWeek, endTime, startTime, intervalId);

        if (!overlaps.isEmpty()) {
            throw new BadRequestAlertException("interval.overlap", "availabilityTemplateInterval", "Interval overlaps with an existing interval for the same template and day");
        }
    }


    public void applyToAllWorkingDays(Long intervalId) {

        LOG.debug("Request to apply interval {} to all working days", intervalId);

        AvailabilityTemplateInterval source = intervalRepository.findById(intervalId)
                .orElseThrow(() ->
                        new BadRequestAlertException(
                                "interval.notfound",
                                "availabilityTemplateInterval",
                                "Interval not found with id " + intervalId));

        AvailabilityTemplate template = source.getTemplate();

        List<DayOfWeek> targetDays = resolveWorkingDays(template);

        targetDays.remove(source.getDayOfWeek());

        List<AvailabilityTemplateIntervalBreak> sourceBreaks =
                availabilityTemplateIntervalBreakRepository
                        .findByInterval_IdOrderByStartTimeAsc(source.getId());

        for (DayOfWeek day : targetDays) {

            if (hasOverlap(
                    template.getId(),
                    day,
                    source.getStartTime(),
                    source.getEndTime())) {
                continue;
            }

            AvailabilityTemplateInterval interval = new AvailabilityTemplateInterval();

            interval.setTemplate(template);
            interval.setDayOfWeek(day);
            interval.setStartTime(source.getStartTime());
            interval.setEndTime(source.getEndTime());
            interval.setSlotStrategy(source.getSlotStrategy());
            interval.setSlotDurationMinutes(source.getSlotDurationMinutes());

            interval = intervalRepository.save(interval);

            if (source.getAllowedServices() != null) {

                List<AvailabilityTemplateAllowedServiceDTO> allowedServices =
                        source.getAllowedServices().stream()
                                .map(service ->
                                        new AvailabilityTemplateAllowedServiceDTO(
                                                null,
                                                service.getService()
                                        ))
                                .toList();

                replaceAllowedServices(interval, template, allowedServices);
            }

            for (AvailabilityTemplateIntervalBreak sourceBreak : sourceBreaks) {

                AvailabilityTemplateIntervalBreak copy =
                        new AvailabilityTemplateIntervalBreak();

                copy.setTemplate(template);
                copy.setInterval(interval);
                copy.setStartTime(sourceBreak.getStartTime());
                copy.setEndTime(sourceBreak.getEndTime());

                availabilityTemplateIntervalBreakRepository.save(copy);
            }
        }
    }
}
