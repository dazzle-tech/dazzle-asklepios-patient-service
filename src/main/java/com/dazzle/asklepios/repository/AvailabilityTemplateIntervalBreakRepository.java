package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AvailabilityTemplateIntervalBreak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;

@Repository
public interface AvailabilityTemplateIntervalBreakRepository extends JpaRepository<AvailabilityTemplateIntervalBreak, Long> {

    List<AvailabilityTemplateIntervalBreak> findByInterval_IdOrderByStartTimeAsc(Long intervalId);

    List<AvailabilityTemplateIntervalBreak> findByInterval_IdAndStartTimeLessThanAndEndTimeGreaterThan(Long intervalId, LocalTime endTime, LocalTime startTime);

    void deleteByInterval_Id(Long intervalId);

    void deleteByTemplate_Id(Long templateId);
}