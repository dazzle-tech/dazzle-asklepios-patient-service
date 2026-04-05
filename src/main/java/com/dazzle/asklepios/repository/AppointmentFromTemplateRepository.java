package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AppointmentFromTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AppointmentFromTemplateRepository extends JpaRepository<AppointmentFromTemplate, Long> {

    List<AppointmentFromTemplate> findByFacilityAndDepartmentAndStartDatetimeBetween(
            Long facility,
            Long department,
            Instant start,
            Instant end
    );
}