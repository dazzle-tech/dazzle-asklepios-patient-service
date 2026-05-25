package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AppointmentPolicyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentPolicyAssignmentRepository extends JpaRepository<AppointmentPolicyAssignment, Long> {
}