package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalTreatmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DentalTreatmentPlanRepository extends JpaRepository<DentalTreatmentPlan, Long> {

    List<DentalTreatmentPlan> findByPatientIdOrderByCreatedDateDesc(Long patientId);

    @Query(value = "SELECT nextval('dental_treatment_plan_seq')", nativeQuery = true)
    Long nextPlanSequence();
}
