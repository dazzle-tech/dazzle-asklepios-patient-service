package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PainAssessment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PainAssessmentRepository extends JpaRepository<PainAssessment, Long> {

    Optional<PainAssessment> findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(Long encounterId);

    Optional<PainAssessment> findFirstByEncounterIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateDesc(
            Long encounterId,
            Instant dayStart,
            Instant dayEnd
    );
    Set<PainAssessment> findDistinctByEncounterIdIn(List<Long> encounterIds);
}
