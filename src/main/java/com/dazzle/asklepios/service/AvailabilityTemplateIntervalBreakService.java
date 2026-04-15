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
                .orElseThrow(() -> new BadRequestAlertException("Interval not found with id " + dto.intervalId(), ENTITY_NAME, "interval.notfound"));

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
            throw new BadRequestAlertException("Break not found with id " + id, ENTITY_NAME, "notfound");
        }
        intervalBreakRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityTemplateIntervalBreak> findByInterval(Long intervalId) {
        LOG.debug("Request to get AvailabilityTemplateIntervalBreak list by intervalId={}", intervalId);

        if (!intervalRepository.existsById(intervalId)) {
            throw new BadRequestAlertException("Interval not found with id " + intervalId, ENTITY_NAME, "interval.notfound");
        }

        return intervalBreakRepository.findByInterval_IdOrderByStartTimeAsc(intervalId);
    }

    private void validateBreak(AvailabilityTemplateInterval interval, LocalTime startTime, LocalTime endTime) {
        if (startTime == null) {
            throw new BadRequestAlertException("Start time is required", ENTITY_NAME, "starttime.required");
        }
        if (endTime == null) {
            throw new BadRequestAlertException("End time is required", ENTITY_NAME, "endtime.required");
        }

        if (!endTime.isAfter(startTime)) {
            throw new BadRequestAlertException("End time must be after start time", ENTITY_NAME, "timerange.invalid");
        }

        if (!startTime.isAfter(interval.getStartTime()) && !startTime.equals(interval.getStartTime())) {
            throw new BadRequestAlertException("Break start time must be within interval time range", ENTITY_NAME, "break.outside.interval");
        }

        if (!endTime.isBefore(interval.getEndTime()) && !endTime.equals(interval.getEndTime())) {
            throw new BadRequestAlertException("Break end time must be within interval time range", ENTITY_NAME, "break.outside.interval");
        }

        if (startTime.isBefore(interval.getStartTime()) || endTime.isAfter(interval.getEndTime())) {
            throw new BadRequestAlertException("Break must be fully inside the interval time range", ENTITY_NAME, "break.outside.interval");
        }
    }

    private void validateNoOverlap(Long intervalId, LocalTime startTime, LocalTime endTime) {
        List<AvailabilityTemplateIntervalBreak> overlaps = intervalBreakRepository.findByInterval_IdAndStartTimeLessThanAndEndTimeGreaterThan(intervalId, endTime, startTime);

        if (!overlaps.isEmpty()) {
            throw new BadRequestAlertException("Break overlaps with an existing break for the same interval", ENTITY_NAME, "break.overlap");
        }
    }
}