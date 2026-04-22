package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalProcedure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DentalProcedureRepository extends JpaRepository<DentalProcedure, Long>,
        JpaSpecificationExecutor<DentalProcedure> {

    Page<DentalProcedure> findByPatientId(Long patientId, Pageable pageable);

    Page<DentalProcedure> findByPatientIdAndCancelledFalse(Long patientId, Pageable pageable);
}