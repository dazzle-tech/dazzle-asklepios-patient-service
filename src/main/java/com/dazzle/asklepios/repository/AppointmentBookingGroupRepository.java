package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AppointmentBookingGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentBookingGroupRepository extends JpaRepository<AppointmentBookingGroup, Long> {}