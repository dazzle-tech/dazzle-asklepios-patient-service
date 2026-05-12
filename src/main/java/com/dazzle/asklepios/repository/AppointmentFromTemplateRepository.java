package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AppointmentFromTemplate;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.BookingMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AppointmentFromTemplateRepository extends JpaRepository<AppointmentFromTemplate, Long>, JpaSpecificationExecutor<AppointmentFromTemplate> {

    Page<AppointmentFromTemplate> findByStatusInAndStartDatetimeBetween(
            List<AppointmentStatus> status,
            Instant startDatetime,
            Instant endDatetime,
            Pageable pageable
    );

    Page<AppointmentFromTemplate> findByDepartmentIdAndStartDatetimeBetween(Long departmentId, Instant startDatetime, Instant endDatetime, Pageable pageable);

    Page<AppointmentFromTemplate> findByAvailabilityGenerationBatch_Id(Long id, Pageable pageable);

    List<AppointmentFromTemplate> findByAvailabilityGenerationBatch_IdAndStatusInAndStartDatetimeGreaterThanOrderByStartDatetimeAsc(
            Long availabilityGenerationBatchId,
            List<AppointmentStatus> statuses,
            Instant startDatetime
    );

    List<AppointmentFromTemplate> findByAvailabilityGenerationBatch_IdAndStatusAndStartDatetimeGreaterThanAndBookingModeInOrderByStartDatetimeAsc(
            Long availabilityGenerationBatchId,
            AppointmentStatus status,
            Instant startDatetime,
            List<BookingMode> bookingMode
    );

}

