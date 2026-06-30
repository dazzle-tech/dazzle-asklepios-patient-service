package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AppointmentWaitingListBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentWaitingListBookingRepository extends JpaRepository<AppointmentWaitingListBooking, Long> {
}