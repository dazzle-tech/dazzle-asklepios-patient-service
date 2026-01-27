package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.VitalSigns;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VitalSignsRepository extends JpaRepository<VitalSigns, Long> {

    Optional<VitalSigns> findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(Long encounterId);

    Optional<VitalSigns> findFirstByEncounterIdAndIsTriageTrueAndIsActiveTrueOrderByCreatedDateDesc(Long encounterId);
}
