package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AdditionalMeasurements;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdditionalMeasurementsRepository extends JpaRepository<AdditionalMeasurements, Long> {

    Optional<AdditionalMeasurements> findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(Long encounterId);

    Optional<AdditionalMeasurements> findFirstByEncounterIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateDesc(
            Long encounterId,
            Instant dayStart,
            Instant dayEnd
    );

}
