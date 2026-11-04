package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.NephrologyKidneyAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NephrologyKidneyAssessmentRepository
        extends JpaRepository<NephrologyKidneyAssessment, Long> {

    Optional<NephrologyKidneyAssessment>
    findFirstByPatientIdAndEncounterIdAndIsActiveTrue(
            Long patientId,
            Long encounterId
    );
}