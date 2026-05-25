package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AppointmentPolicyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentPolicyAssignmentRepository extends JpaRepository<AppointmentPolicyAssignment, Long> {
    List<AppointmentPolicyAssignment> findByAppointment_Id(Long appointmentId);
}