package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, String>, JpaSpecificationExecutor<Appointment> {

    @Query("SELECT a FROM Appointment a WHERE a.isValid = true AND a.deletedAt IS NULL")
    List<Appointment> findAllActive();
}

