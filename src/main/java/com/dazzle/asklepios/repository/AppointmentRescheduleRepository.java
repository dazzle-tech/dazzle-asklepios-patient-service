package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AppointmentReschedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentRescheduleRepository extends JpaRepository<AppointmentReschedule, Long> {
}