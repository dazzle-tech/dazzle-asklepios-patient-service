package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BodyMeasurements;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BodyMeasurementsRepository extends JpaRepository<BodyMeasurements, Long> {

    Optional<BodyMeasurements> findFirstByPatient_IdAndIsActiveTrueOrderByCreatedDateDesc(Long patientId);

    Optional<BodyMeasurements> findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(Long encounterId);

    Page<BodyMeasurements> findByPatientIdAndIsActiveTrueAndCreatedDateBetween(
            Long patientId,
            Instant from,
            Instant to,
            Pageable pageable
    );

    List<BodyMeasurements> findByPatientIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateAsc(
            Long patientId,
            Instant from,
            Instant to
    );

    Optional<BodyMeasurements> findFirstByEncounterIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateDesc(
            Long encounterId,
            Instant dayStart,
            Instant dayEnd
    );

    Page<BodyMeasurements> findByPatientIdAndIsActiveTrue(
            Long patientId,
            Pageable pageable
    );


    Optional<BodyMeasurements> findFirstByPatientIdAndHeightIsNotNullOrderByCreatedDateDesc(Long patientId);

    Optional<BodyMeasurements> findFirstByPatientIdAndWeightIsNotNullOrderByCreatedDateDesc(Long patientId);
}
