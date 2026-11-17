package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DentalPlannedTreatmentRepository extends JpaRepository<DentalPlannedTreatment, Long> {

    List<DentalPlannedTreatment> findByPatientIdAndTypeAndCancelledFalseOrderByVisitNumberAsc(Long patientId, PlannedTreatmentType type);

    List<DentalPlannedTreatment> findByEncounterIdAndTypeAndCancelledFalseOrderByCreatedDateAsc(Long encounterId, PlannedTreatmentType type);

    List<DentalPlannedTreatment> findByToothActionIdAndCancelledFalse(Long toothActionId);

    /** Open (not cancelled, not completed) planned treatments per tooth: rows of [toothId, count]. */
    @Query("""
            select p.toothId, count(p)
            from DentalPlannedTreatment p
            where p.toothId in :toothIds
              and p.cancelled = false
              and p.status <> :completedStatus
            group by p.toothId
            """)
    List<Object[]> countOpenByToothIds(
            @Param("toothIds") Collection<Long> toothIds,
            @Param("completedStatus") PlannedTreatmentStatus completedStatus
    );

    /** Non-cancelled treatments in the given statuses that were planned on, or executed on, the encounter. */
    @Query("""
            select p
            from DentalPlannedTreatment p
            where p.cancelled = false
              and p.status in :statuses
              and (p.encounterId = :encounterId or p.executionEncounterId = :encounterId)
            """)
    List<DentalPlannedTreatment> findTouchingEncounter(
            @Param("encounterId") Long encounterId,
            @Param("statuses") Collection<PlannedTreatmentStatus> statuses
    );

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
