// src/main/java/com/dazzle/asklepios/repository/DiagnosticOrderRepository.java
package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiagnosticOrderRepository extends JpaRepository<DiagnosticOrder, Long> {

    Page<DiagnosticOrder> findByEncounterId(Long encounterId, Pageable pageable);

    Page<DiagnosticOrder> findByPatientId(Long patientId, Pageable pageable);

    Page<DiagnosticOrder> findByPatientIdAndEncounterId(Long patientId, Long encounterId, Pageable pageable);

    Page<DiagnosticOrder> findByEncounterIdAndStatus(Long encounterId, String status, Pageable pageable);

    Page<DiagnosticOrder> findByPatientIdAndStatus(Long patientId, String status, Pageable pageable);

    Page<DiagnosticOrder> findByPatientIdAndEncounterIdAndStatus(Long patientId, Long encounterId, String status, Pageable pageable);
}
