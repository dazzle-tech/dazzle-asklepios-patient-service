package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PainAssessment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PainAssessmentRepository extends JpaRepository<PainAssessment, Long> {

    Optional<PainAssessment> findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(Long encounterId);
}
