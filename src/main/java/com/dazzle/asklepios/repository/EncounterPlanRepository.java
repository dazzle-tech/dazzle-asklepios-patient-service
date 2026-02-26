package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EncounterPlanRepository extends JpaRepository<EncounterPlan, Long> {

    Optional<EncounterPlan> findTopByEncounterIdOrderByCreatedDateDesc(Long encounterId);
}
