package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ToothActionRepository extends JpaRepository<ToothAction, Long> {

    List<ToothAction> findByToothId(Long toothId);

    List<ToothAction> findByToothIdAndCancelledFalse(Long toothId);

    List<ToothAction> findByToothIdIn(List<Long> toothIds);

    List<ToothAction> findByToothIdInAndCancelledFalse(List<Long> toothIds);

    Optional<ToothAction> findByToothIdAndDentalActionIdAndSurfaceAndCancelledFalse(Long toothId, Long dentalActionId, ToothSurface surface);

    /**
     * Highest Visit Number on tooth actions for the patient (any chart, including cancelled).
     */
    @Query(value = """
            select max(ta.visit_number)
            from tooth_action ta
            join dental_chart_tooth dct on dct.id = ta.tooth_id
            join dental_chart dc on dc.id = dct.chart_id
            where dc.patient_id = :patientId
            """, nativeQuery = true)
    Integer findMaxVisitNumberByPatientId(@Param("patientId") Long patientId);

    @Query(value = """
            select distinct ta.visit_number
            from tooth_action ta
            join dental_chart_tooth dct on dct.id = ta.tooth_id
            join dental_chart dc on dc.id = dct.chart_id
            where dc.patient_id = :patientId
              and ta.visit_number is not null
            """, nativeQuery = true)
    List<Integer> findDistinctVisitNumbersByPatientId(@Param("patientId") Long patientId);
}
