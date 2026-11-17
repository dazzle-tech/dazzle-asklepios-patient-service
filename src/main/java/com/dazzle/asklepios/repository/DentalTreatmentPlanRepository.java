package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalTreatmentPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface DentalTreatmentPlanRepository extends JpaRepository<DentalTreatmentPlan, Long> {

    List<DentalTreatmentPlan> findByPatientIdOrderByCreatedDateDesc(Long patientId);

    /** Row lock that serializes session allocation and plan reopening per plan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DentalTreatmentPlan> findForUpdateById(Long id);
}
