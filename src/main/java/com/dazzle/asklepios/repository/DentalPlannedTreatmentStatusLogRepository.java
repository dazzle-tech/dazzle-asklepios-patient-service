package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalPlannedTreatmentStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DentalPlannedTreatmentStatusLogRepository extends JpaRepository<DentalPlannedTreatmentStatusLog, Long> {

    List<DentalPlannedTreatmentStatusLog> findByTreatmentPlanIdOrderByChangedAtDescIdDesc(Long treatmentPlanId);
}
