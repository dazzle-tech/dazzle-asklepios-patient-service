package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalTreatmentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DentalTreatmentSessionRepository extends JpaRepository<DentalTreatmentSession, Long> {

    List<DentalTreatmentSession> findByTreatmentPlanIdOrderBySessionNumberAsc(Long treatmentPlanId);

    @Query("""
            select max(s.sessionNumber)
            from DentalTreatmentSession s
            where s.treatmentPlanId = :planId
            """)
    Integer findMaxSessionNumber(@Param("planId") Long planId);
}
