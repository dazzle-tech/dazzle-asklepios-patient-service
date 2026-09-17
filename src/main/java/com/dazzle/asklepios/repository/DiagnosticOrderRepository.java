package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Repository
public interface DiagnosticOrderRepository extends JpaRepository<DiagnosticOrder, Long>  , JpaSpecificationExecutor<DiagnosticOrder> {

    Page<DiagnosticOrder> findByEncounterId(Long encounterId, Pageable pageable);

    List<DiagnosticOrder> findByEncounterIdAndStatusNot(Long encounterId, DiagnosticStatus status);

    Page<DiagnosticOrder> findByPatient_Id(Long patientId, Pageable pageable);

    Page<DiagnosticOrder> findByPatient_IdAndEncounterId(Long patientId, Long encounterId, Pageable pageable);

    Page<DiagnosticOrder> findByEncounterIdAndStatus(Long encounterId, DiagnosticStatus status, Pageable pageable);

    Page<DiagnosticOrder> findByPatient_IdAndStatus(Long patientId, DiagnosticStatus status, Pageable pageable);

    Page<DiagnosticOrder> findByPatient_IdAndEncounterIdAndStatus(Long patientId, Long encounterId, DiagnosticStatus status, Pageable pageable);

    @Query("""
        select o.id
        from DiagnosticOrder o
        where o.patientId = :patientId
          and o.createdDate between :from and :to
    """)
    List<Long> findIdsByPatientIdAndCreatedDateBetween(Long patientId, Instant from, Instant to);



    Set<DiagnosticOrder> findDistinctByEncounterIdIn(List<Long> encounterIds);

}
