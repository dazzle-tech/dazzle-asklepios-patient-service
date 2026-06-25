package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EncounterAssessmentRepository extends JpaRepository<EncounterAssessment, Long> {

    Optional<EncounterAssessment> findTopByEncounterIdAndOrderByCreatedDateDesc(Long encounterId);}
