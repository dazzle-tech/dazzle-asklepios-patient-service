package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.repository.projection.VisitNumberView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DentalPlannedTreatmentRepository extends JpaRepository<DentalPlannedTreatment, Long> {

    List<DentalPlannedTreatment> findByPatientIdAndTypeAndCancelledFalseOrderByVisitNumberAsc(Long patientId, PlannedTreatmentType type);

    List<DentalPlannedTreatment> findByEncounterIdAndTypeAndCancelledFalseOrderByCreatedDateAsc(Long encounterId, PlannedTreatmentType type);

    List<DentalPlannedTreatment> findByToothActionIdAndCancelledFalse(Long toothActionId);

    List<DentalPlannedTreatment> findByTreatmentPlanId(Long treatmentPlanId);

    List<DentalPlannedTreatment> findByTreatmentPlanIdAndCancelledFalseOrderByVisitNumberAsc(Long treatmentPlanId);

    /**
     * Row holding the highest Visit Number for the patient across all encounters, DRAFT/CURRENT, including cancelled.
     */
    Optional<DentalPlannedTreatment> findFirstByPatientIdAndVisitNumberNotNullOrderByVisitNumberDesc(Long patientId);

    List<VisitNumberView> findDistinctByPatientIdAndVisitNumberNotNull(Long patientId);
}
