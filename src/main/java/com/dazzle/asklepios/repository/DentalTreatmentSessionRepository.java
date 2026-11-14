package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalTreatmentSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DentalTreatmentSessionRepository extends JpaRepository<DentalTreatmentSession, Long> {

    List<DentalTreatmentSession> findByTreatmentPlanIdOrderBySessionNumberAsc(Long treatmentPlanId);

    Optional<DentalTreatmentSession> findFirstByTreatmentPlanIdOrderBySessionNumberDesc(Long treatmentPlanId);
}
