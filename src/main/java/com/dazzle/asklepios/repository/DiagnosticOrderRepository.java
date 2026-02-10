package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DiagnosticOrderRepository extends JpaRepository<DiagnosticOrder, Long>  , JpaSpecificationExecutor<DiagnosticOrder> {

    Page<DiagnosticOrder> findByEncounterId(Long encounterId, Pageable pageable);

    Page<DiagnosticOrder> findByPatient_Id(Long patientId, Pageable pageable);

    Page<DiagnosticOrder> findByPatient_IdAndEncounterId(Long patientId, Long encounterId, Pageable pageable);

    Page<DiagnosticOrder> findByEncounterIdAndStatus(Long encounterId, DiagnosticStatus status, Pageable pageable);

    Page<DiagnosticOrder> findByPatient_IdAndStatus(Long patientId, DiagnosticStatus status, Pageable pageable);

    Page<DiagnosticOrder> findByPatient_IdAndEncounterIdAndStatus(Long patientId, Long encounterId, DiagnosticStatus status, Pageable pageable);
}
