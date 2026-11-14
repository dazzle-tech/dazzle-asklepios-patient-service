package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalTreatmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DentalTreatmentPlanRepository extends JpaRepository<DentalTreatmentPlan, Long> {

    List<DentalTreatmentPlan> findByPatientIdOrderByCreatedDateDesc(Long patientId);
}
