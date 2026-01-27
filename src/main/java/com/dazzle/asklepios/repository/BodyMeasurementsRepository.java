package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BodyMeasurements;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BodyMeasurementsRepository extends JpaRepository<BodyMeasurements, Long> {

    Optional<BodyMeasurements> findFirstByPatient_IdAndIsActiveTrueOrderByCreatedDateDesc(Long patientId);

    Optional<BodyMeasurements> findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(Long encounterId);
}
