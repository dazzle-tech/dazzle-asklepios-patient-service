package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.NephrologyTreatmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NephrologyTreatmentPlanRepository
        extends JpaRepository<NephrologyTreatmentPlan, Long> {

    Optional<NephrologyTreatmentPlan>
    findFirstByPatientIdAndEncounterIdAndIsActiveTrue(
            Long patientId,
            Long encounterId
    );
}
