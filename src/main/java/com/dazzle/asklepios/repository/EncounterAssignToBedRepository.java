package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterAssignToBed;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EncounterAssignToBedRepository extends JpaRepository<EncounterAssignToBed, Long> {
    
    Optional<EncounterAssignToBed> findByEncounter_IdAndIsActiveTrue(Long encounterId);

    List<EncounterAssignToBed> findAllByEncounter_IdInAndIsActiveTrue(List<Long> encounterIds);


    boolean existsByBedIdAndIsActiveTrue(Long bedId);
}