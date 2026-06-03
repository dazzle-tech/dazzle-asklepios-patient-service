package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Appointment;
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
public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    Page<Appointment> findByStatusInAndStartDatetimeBetween(List<AppointmentStatus> status, Instant startDatetime, Instant endDatetime, Pageable pageable);

    Page<Appointment> findByDepartmentIdAndStartDatetimeBetween(Long departmentId, Instant startDatetime, Instant endDatetime, Pageable pageable);

    Page<Appointment> findByAvailabilityGenerationBatch_Id(Long id, Pageable pageable);

    List<Appointment> findByAvailabilityGenerationBatch_IdAndStatusInAndStartDatetimeGreaterThanOrderByStartDatetimeAsc(Long availabilityGenerationBatchId, List<AppointmentStatus> statuses, Instant startDatetime);

    List<Appointment> findByAvailabilityGenerationBatch_IdAndStatusAndStartDatetimeGreaterThanAndBookingModeInOrderByStartDatetimeAsc(Long availabilityGenerationBatchId, AppointmentStatus status, Instant startDatetime, List<BookingMode> bookingMode);

    List<Appointment> findByStatusInAndStartDatetimeBetween(List<AppointmentStatus> status, Instant startDatetime, Instant endDatetime);

    List<Appointment> findByStatusInAndStartDatetimeBefore(
            List<AppointmentStatus> statuses,
            Instant cutoff
    );
}

