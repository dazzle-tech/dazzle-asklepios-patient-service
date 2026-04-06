package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.AvailabilityTemplate;
import com.dazzle.asklepios.domain.AvailabilityTemplateAllowedService;
import com.dazzle.asklepios.domain.AvailabilityTemplateInterval;
import com.dazzle.asklepios.domain.enumeration.DayOfWeek;
import com.dazzle.asklepios.repository.AvailabilityTemplateAllowedServiceRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateIntervalRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateRepository;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateAllowedServices.AvailabilityTemplateAllowedServiceDTO;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateInterval.AvailabilityTemplateIntervalCreateDTO;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateInterval.AvailabilityTemplateIntervalUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class AvailabilityTemplateIntervalService {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityTemplateIntervalService.class);

    private final AvailabilityTemplateIntervalRepository intervalRepository;
    private final AvailabilityTemplateAllowedServiceRepository allowedServiceRepository;
    private final AvailabilityTemplateRepository templateRepository;

    public AvailabilityTemplateIntervalService(AvailabilityTemplateIntervalRepository intervalRepository, AvailabilityTemplateAllowedServiceRepository allowedServiceRepository, AvailabilityTemplateRepository templateRepository) {
        this.intervalRepository = intervalRepository;
        this.allowedServiceRepository = allowedServiceRepository;
        this.templateRepository = templateRepository;
    }

    public AvailabilityTemplateInterval create(AvailabilityTemplateIntervalCreateDTO dto) {
        LOG.debug("Request to create AvailabilityTemplateInterval: {}", dto);

        if (dto == null) {
            throw new BadRequestAlertException("Interval payload is required", "availabilityTemplateInterval", "payload.required");
        }

        AvailabilityTemplate template = templateRepository.findById(dto.templateId())
                .orElseThrow(() -> new BadRequestAlertException("Template not found with id " + dto.templateId(), "availabilityTemplateInterval", "template.notfound"));

        validateInterval(dto.dayOfWeek(), dto.startTime(), dto.endTime(), dto.slotDurationMinutes());
        validateNoOverlap(template.getId(), dto.dayOfWeek(), dto.startTime(), dto.endTime());

        AvailabilityTemplateInterval interval = new AvailabilityTemplateInterval();
        interval.setTemplate(template);
        interval.setDayOfWeek(dto.dayOfWeek());
        interval.setStartTime(dto.startTime());
        interval.setEndTime(dto.endTime());
        interval.setSlotStrategy(dto.slotStrategy());
        interval.setSlotDurationMinutes(dto.slotDurationMinutes());

        AvailabilityTemplateInterval savedInterval = intervalRepository.save(interval);

        List<AvailabilityTemplateAllowedService> savedAllowedServices = replaceAllowedServices(savedInterval, template, dto.allowedServices());

        savedInterval.setAllowedServices(savedAllowedServices);

        return savedInterval;
    }

    public Optional<AvailabilityTemplateInterval> update(Long id, AvailabilityTemplateIntervalUpdateDTO dto) {
        LOG.debug("Request to update AvailabilityTemplateInterval id={} with {}", id, dto);

        AvailabilityTemplateInterval interval = intervalRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException("Interval not found with id " + id, "availabilityTemplateInterval", "notfound"));

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
            throw new BadRequestAlertException("Day of week is required", "availabilityTemplateInterval", "dayofweek.required");
        }

        if (startTime == null) {
            throw new BadRequestAlertException("Start time is required", "availabilityTemplateInterval", "starttime.required");
        }

        if (endTime == null) {
            throw new BadRequestAlertException("End time is required", "availabilityTemplateInterval", "endtime.required");
        }

        if (!endTime.isAfter(startTime)) {
            throw new BadRequestAlertException("End time must be after start time", "availabilityTemplateInterval", "timerange.invalid");
        }

        if (slotDurationMinutes == null || slotDurationMinutes <= 0) {
            throw new BadRequestAlertException("Slot duration minutes must be greater than 0", "availabilityTemplateInterval", "slotduration.invalid");
        }
    }
    private void validateNoOverlap(Long templateId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime ) {
        List<AvailabilityTemplateInterval> overlaps = intervalRepository.findByTemplate_IdAndDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThan(templateId, dayOfWeek, endTime, startTime);

        if (!overlaps.isEmpty()) {
            throw new BadRequestAlertException("Interval overlaps with an existing interval for the same template and day", "availabilityTemplateInterval", "interval.overlap");
        }
    }

    private void validateNoOverlapForUpdate(Long intervalId, Long templateId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        List<AvailabilityTemplateInterval> overlaps =
                intervalRepository.findByTemplate_IdAndDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(templateId, dayOfWeek, endTime, startTime, intervalId);

        if (!overlaps.isEmpty()) {
            throw new BadRequestAlertException("Interval overlaps with an existing interval for the same template and day", "availabilityTemplateInterval", "interval.overlap");
        }
    }

}
