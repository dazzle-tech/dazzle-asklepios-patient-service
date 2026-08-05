package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.VitalSigns;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface VitalSignsRepository extends JpaRepository<VitalSigns, Long> {

    Optional<VitalSigns> findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(Long encounterId);

    Optional<VitalSigns> findFirstByEncounterIdAndIsTriageTrueAndIsActiveTrueOrderByCreatedDateDesc(Long encounterId);

    Page<VitalSigns> findByPatientIdAndIsActiveTrueAndCreatedDateBetween(
            Long patientId,
            Instant from,
            Instant to,
            Pageable pageable
    );

    List<VitalSigns> findByPatientIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateAsc(
            Long patientId,
            Instant from,
            Instant to
    );

    List<VitalSigns> findByEncounterIdAndIsActiveTrueAndCreatedDateBetween(
            Long encounterId,
            Instant dayStart,
            Instant dayEnd
    );

    Optional<VitalSigns> findFirstByEncounterIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateDesc(
            Long encounterId,
            Instant dayStart,
            Instant dayEnd
    );

    Page<VitalSigns> findByPatientIdAndIsActiveTrue(
            Long patientId,
            Pageable pageable
    );

    Set<VitalSigns> findDistinctByEncounterIdIn(List<Long> encounterIds);

    Optional<VitalSigns> findTopByEncounterIdAndIsActiveTrueOrderByIdDesc(Long encounterId);
}