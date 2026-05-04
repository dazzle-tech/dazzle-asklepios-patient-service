package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AvailabilityTemplateInterval;
import com.dazzle.asklepios.domain.enumeration.DayOfWeek;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AvailabilityTemplateIntervalRepository extends JpaRepository<AvailabilityTemplateInterval, Long> {

    @EntityGraph(attributePaths = {"allowedServices"})
    List<AvailabilityTemplateInterval> findByTemplate_IdAndDayOfWeek(Long templateId, DayOfWeek dayOfWeek);

    @EntityGraph(attributePaths = {"allowedServices"})
    Optional<AvailabilityTemplateInterval> findWithAllowedServicesById(Long id);

    List<AvailabilityTemplateInterval> findByTemplate_IdAndDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThan(Long templateId, DayOfWeek dayOfWeek, LocalTime endTime, LocalTime startTime);

    List<AvailabilityTemplateInterval> findByTemplate_IdAndDayOfWeekAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(Long templateId, DayOfWeek dayOfWeek, LocalTime endTime, LocalTime startTime, Long id);

    void deleteByTemplate_Id(Long templateId);

    boolean existsByTemplate_Id(Long templateId);

    @EntityGraph(attributePaths = {"allowedServices"})
    List<AvailabilityTemplateInterval> findByTemplate_Id(Long templateId);
}