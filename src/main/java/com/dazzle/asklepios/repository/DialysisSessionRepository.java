package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DialysisSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DialysisSessionRepository
        extends JpaRepository<DialysisSession, Long> {

    Optional<DialysisSession>
    findFirstByPatientIdAndEncounterIdAndIsActiveTrueOrderByLastModifiedDateDesc(
            Long patientId,
            Long encounterId
    );

    Optional<DialysisSession>
    findFirstByPatientIdAndEncounterIdAndTreatmentPlanIdAndIsActiveTrueOrderByLastModifiedDateDesc(
            Long patientId,
            Long encounterId,
            Long treatmentPlanId
    );

    Optional<DialysisSession>
    findFirstByPatientIdAndEncounterIdAndTreatmentPlanIdIsNullAndIsActiveTrueOrderByLastModifiedDateDesc(
            Long patientId,
            Long encounterId
    );

    List<DialysisSession>
    findByPatientIdAndEncounterIdAndTreatmentPlanIdAndIsActiveTrueOrderByLastModifiedDateDesc(
            Long patientId,
            Long encounterId,
            Long treatmentPlanId
    );

    Page<DialysisSession> findByPatientIdAndEncounterIdAndIsActiveTrue(
            Long patientId,
            Long encounterId,
            Pageable pageable
    );

    Page<DialysisSession>
    findByPatientIdAndEncounterIdAndTreatmentPlanIdAndIsActiveTrue(
            Long patientId,
            Long encounterId,
            Long treatmentPlanId,
            Pageable pageable
    );

    Optional<DialysisSession> findByIdAndPatientIdAndEncounterIdAndIsActiveTrue(
            Long id,
            Long patientId,
            Long encounterId
    );

    boolean existsByPatientIdAndEncounterIdAndTreatmentPlanIdAndIsActiveTrueAndIdNot(
            Long patientId,
            Long encounterId,
            Long treatmentPlanId,
            Long id
    );

    boolean existsByPatientIdAndEncounterIdAndTreatmentPlanIdAndIsActiveTrue(
            Long patientId,
            Long encounterId,
            Long treatmentPlanId
    );

    boolean existsByPatientIdAndEncounterIdAndTreatmentPlanIdIsNullAndIsActiveTrue(
            Long patientId,
            Long encounterId
    );

    boolean existsByPatientIdAndEncounterIdAndTreatmentPlanIdIsNullAndIsActiveTrueAndIdNot(
            Long patientId,
            Long encounterId,
            Long id
    );
}
