package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientServiceAndProductRepository extends JpaRepository<PatientServiceAndProduct, Long> {

    Page<PatientServiceAndProduct> findAllByEncounterId(Long encounterId, Pageable pageable);

    Page<PatientServiceAndProduct> findAllByPatientId(Long patientId, Pageable pageable);

    boolean existsByPatientIdAndEncounterIdAndDiagnosticTestIdAndIsBilledTrue(
            Long patientId,
            Long encounterId,
            Long diagnosticTestId
    );

    int deleteByPatientIdAndEncounterIdAndDiagnosticTestIdAndIsBilledFalse(
            Long patientId,
            Long encounterId,
            Long diagnosticTestId
    );

    Page<PatientServiceAndProduct> findAllByEncounterIdAndServiceSourceAndSourceId(
            Long encounterId,
            ServiceSource serviceSource,
            Long sourceId,
            Pageable pageable
    );
}