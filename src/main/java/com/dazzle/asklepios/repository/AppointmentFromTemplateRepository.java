package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AppointmentFromTemplate;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AppointmentFromTemplateRepository extends JpaRepository<AppointmentFromTemplate, Long>, JpaSpecificationExecutor<AppointmentFromTemplate> {

    Page<AppointmentFromTemplate> findByStatusAndStartDatetimeBetween(
            AppointmentStatus status,
            Instant startDatetime,
            Instant endDatetime,
            Pageable pageable
    );

    Page<AppointmentFromTemplate> findByAvailabilityGenerationBatch_Id(Long id, Pageable pageable);

}

