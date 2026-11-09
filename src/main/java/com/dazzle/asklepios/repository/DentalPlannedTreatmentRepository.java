package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DentalPlannedTreatmentRepository extends JpaRepository<DentalPlannedTreatment, Long> {

    List<DentalPlannedTreatment> findByPatientIdAndTypeAndCancelledFalseOrderByVisitNumberAsc(Long patientId, PlannedTreatmentType type);

    List<DentalPlannedTreatment> findByEncounterIdAndTypeAndCancelledFalseOrderByCreatedDateAsc(Long encounterId, PlannedTreatmentType type);

    List<DentalPlannedTreatment> findByToothActionIdAndCancelledFalse(Long toothActionId);

    List<DentalPlannedTreatment> findByTreatmentPlanId(Long treatmentPlanId);

    List<DentalPlannedTreatment> findByTreatmentPlanIdAndCancelledFalseOrderByVisitNumberAsc(Long treatmentPlanId);

    /**
     * Highest Visit Number for the patient across all encounters, DRAFT/CURRENT, including cancelled.
     */
    @Query("""
            select max(t.visitNumber)
            from DentalPlannedTreatment t
            where t.patientId = :patientId
            """)
    Integer findMaxVisitNumberByPatientId(@Param("patientId") Long patientId);

    @Query("""
            select distinct t.visitNumber
            from DentalPlannedTreatment t
            where t.patientId = :patientId
              and t.visitNumber is not null
            """)
    List<Integer> findDistinctVisitNumbersByPatientId(@Param("patientId") Long patientId);
}
