package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DentalPlannedTreatmentRepository extends JpaRepository<DentalPlannedTreatment, Long> {

    List<DentalPlannedTreatment> findByPatientIdAndTypeAndCancelledFalseOrderByVisitNumberAsc(Long patientId, PlannedTreatmentType type);

    List<DentalPlannedTreatment> findByEncounterIdAndTypeAndCancelledFalseOrderByCreatedDateAsc(Long encounterId, PlannedTreatmentType type);

    List<DentalPlannedTreatment> findByToothActionIdAndCancelledFalse(Long toothActionId);
}
