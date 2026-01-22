package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.VitalSigns;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VitalSignsRepository extends JpaRepository<VitalSigns, Long> {

    Optional<VitalSigns> findFirstByPatient_IdOrderByCreatedDateDesc(Long patientId);

    Optional<VitalSigns> findFirstByEncounterIdOrderByCreatedDateDesc(Long encounterId);

    Optional<VitalSigns> findFirstByEncounterIdAndIsTriageTrueOrderByCreatedDateDesc(Long encounterId);

}
