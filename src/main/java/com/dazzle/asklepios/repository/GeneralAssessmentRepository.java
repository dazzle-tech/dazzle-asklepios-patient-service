package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.GeneralAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GeneralAssessmentRepository extends JpaRepository<GeneralAssessment, Long> {

    Optional<GeneralAssessment> findTopByEncounterIdOrderByCreatedDateDesc(Long encounterId);


    Optional<GeneralAssessment> findTopByEncounterIdAndIsTriageTrueOrderByCreatedDateDesc(Long encounterId);
}