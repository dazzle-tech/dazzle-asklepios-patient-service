package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EmergencyTriage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmergencyTriageRepository extends JpaRepository<EmergencyTriage, Long> {
    Optional<EmergencyTriage> findTopByEncounter_IdOrderByCreatedDateDesc(Long encounterId);
    List<EmergencyTriage> findAllByEncounterIdIn(List<Long> encounterIds);

}