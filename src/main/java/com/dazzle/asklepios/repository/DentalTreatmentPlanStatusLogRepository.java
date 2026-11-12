package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalTreatmentPlanStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DentalTreatmentPlanStatusLogRepository extends JpaRepository<DentalTreatmentPlanStatusLog, Long> {

    List<DentalTreatmentPlanStatusLog> findByTreatmentPlanIdOrderByChangedAtAsc(Long treatmentPlanId);
}
