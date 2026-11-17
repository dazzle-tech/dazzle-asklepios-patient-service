package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ToothActionRepository extends JpaRepository<ToothAction, Long> {

    List<ToothAction> findByToothId(Long toothId);

    List<ToothAction> findByToothIdAndCancelledFalse(Long toothId);

    List<ToothAction> findByToothIdIn(List<Long> toothIds);

    List<ToothAction> findByToothIdInAndCancelledFalse(List<Long> toothIds);

    Optional<ToothAction> findByToothIdAndDentalActionIdAndSurfaceAndCancelledFalse(Long toothId, Long dentalActionId, ToothSurface surface);

    Optional<ToothAction> findBySourcePlannedTreatmentIdAndToothIdAndCancelledFalse(Long sourcePlannedTreatmentId, Long toothId);

    /** Chart effects recorded by a planned treatment's completion, with their carried-forward copies. */
    List<ToothAction> findBySourcePlannedTreatmentIdAndCancelledFalse(Long sourcePlannedTreatmentId);

    /** Teeth that carry at least one non-cancelled action (finding, treatment or carried-forward finding). */
    @Query("""
            select distinct ta.toothId
            from ToothAction ta
            where ta.toothId in :toothIds
              and ta.cancelled = false
            """)
    List<Long> findToothIdsWithActiveActions(@Param("toothIds") Collection<Long> toothIds);

    /**
     * Highest Visit Number on tooth actions for the patient (any chart, including cancelled).
     */
    @Query("""
            select max(ta.visitNumber)
            from ToothAction ta, DentalChartTooth dct, DentalChart dc
            where dct.id = ta.toothId
              and dc.id = dct.chartId
              and dc.patient.id = :patientId
            """)
    Integer findMaxVisitNumberByPatientId(@Param("patientId") Long patientId);

    @Query("""
            select distinct ta.visitNumber
            from ToothAction ta, DentalChartTooth dct, DentalChart dc
            where dct.id = ta.toothId
              and dc.id = dct.chartId
              and dc.patient.id = :patientId
              and ta.visitNumber is not null
            """)
    List<Integer> findDistinctVisitNumbersByPatientId(@Param("patientId") Long patientId);
}
