package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientProcedureRepository
        extends JpaRepository<PatientProcedure, Long> {

    Page<PatientProcedure> findByEncounterId(
            Long encounterId,
            Pageable pageable
    );

    Page<PatientProcedure> findByEncounterIdAndStatusNot(
            Long encounterId,
            ProcStatus status,
            Pageable pageable
    );
}
