package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AppointmentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentLogRepository extends JpaRepository<AppointmentLog, Long> {

    List<AppointmentLog> findAllByAppointmentIdOrderByLogDateDesc(Long appointmentId);
}
