package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.AvailabilityTemplateInterval;
import com.dazzle.asklepios.domain.AvailabilityTemplateIntervalBreak;
import com.dazzle.asklepios.repository.AvailabilityTemplateIntervalBreakRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateIntervalRepository;
import com.dazzle.asklepios.repository.AvailabilityTemplateRepository;
import com.dazzle.asklepios.service.dto.availabilityTemplate.availabilityTemplateIntervalBreak.AvailabilityTemplateIntervalBreakCreateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
@Transactional
public class AvailabilityTemplateIntervalBreakService {

    private static final Logger LOG = LoggerFactory.getLogger(AvailabilityTemplateIntervalBreakService.class);
    private static final String ENTITY_NAME = "availabilityTemplateIntervalBreak";

    private final AvailabilityTemplateIntervalBreakRepository intervalBreakRepository;
    private final AvailabilityTemplateIntervalRepository intervalRepository;

    public AvailabilityTemplateIntervalBreakService(AvailabilityTemplateIntervalBreakRepository intervalBreakRepository, AvailabilityTemplateIntervalRepository intervalRepository) {
        this.intervalBreakRepository = intervalBreakRepository;
        this.intervalRepository = intervalRepository;
    }

    public AvailabilityTemplateIntervalBreak create(AvailabilityTemplateIntervalBreakCreateDTO dto) {
        LOG.debug("Request to create AvailabilityTemplateIntervalBreak: {}", dto);

        AvailabilityTemplateInterval interval = intervalRepository.findById(dto.intervalId())
                .orElseThrow(() -> new BadRequestAlertException("interval.notfound", ENTITY_NAME, "Interval not found with id " + dto.intervalId()));

        validateBreak(interval, dto.startTime(), dto.endTime());
        validateNoOverlap(interval.getId(), dto.startTime(), dto.endTime());

        AvailabilityTemplateIntervalBreak entity = new AvailabilityTemplateIntervalBreak();
        entity.setTemplate(interval.getTemplate());
        entity.setInterval(interval);
        entity.setStartTime(dto.startTime());
        entity.setEndTime(dto.endTime());

        return intervalBreakRepository.save(entity);
    }

    public void hardDelete(Long id) {
        LOG.debug("Request to hard delete AvailabilityTemplateIntervalBreak id={}", id);

        if (!intervalBreakRepository.existsById(id)) {
            throw new BadRequestAlertException("notfound", ENTITY_NAME, "Break not found with id " + id);
        }
        intervalBreakRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityTemplateIntervalBreak> findByInterval(Long intervalId) {
        LOG.debug("Request to get AvailabilityTemplateIntervalBreak list by intervalId={}", intervalId);

        if (!intervalRepository.existsById(intervalId)) {
            throw new BadRequestAlertException("interval.notfound", ENTITY_NAME, "Interval not found with id " + intervalId);
        }

        return intervalBreakRepository.findByInterval_IdOrderByStartTimeAsc(intervalId);
    }

    private void validateBreak(AvailabilityTemplateInterval interval, LocalTime startTime, LocalTime endTime) {
        if (startTime == null) {
            throw new BadRequestAlertException("starttime.required", ENTITY_NAME, "Start time is required");
        }
        if (endTime == null) {
            throw new BadRequestAlertException( "endtime.required", ENTITY_NAME,"End time is required");
        }

        if (!endTime.isAfter(startTime)) {
            throw new BadRequestAlertException("timerange.invalid", ENTITY_NAME, "End time must be after start time");
        }

        if (!startTime.isAfter(interval.getStartTime()) && !startTime.equals(interval.getStartTime())) {
            throw new BadRequestAlertException("break.outside.interval" , ENTITY_NAME, "Break start time must be within interval time range");
        }

        if (!endTime.isBefore(interval.getEndTime()) && !endTime.equals(interval.getEndTime())) {
            throw new BadRequestAlertException("break.outside.interval", ENTITY_NAME, "Break end time must be within interval time range");
        }

        if (startTime.isBefore(interval.getStartTime()) || endTime.isAfter(interval.getEndTime())) {
            throw new BadRequestAlertException("break.outside.interval", ENTITY_NAME, "Break must be fully inside the interval time range");
        }
    }

    private void validateNoOverlap(Long intervalId, LocalTime startTime, LocalTime endTime) {
        List<AvailabilityTemplateIntervalBreak> overlaps = intervalBreakRepository.findByInterval_IdAndStartTimeLessThanAndEndTimeGreaterThan(intervalId, endTime, startTime);

        if (!overlaps.isEmpty()) {
            throw new BadRequestAlertException("break.overlap", ENTITY_NAME, "Break overlaps with an existing break for the same interval");
        }
    }
}