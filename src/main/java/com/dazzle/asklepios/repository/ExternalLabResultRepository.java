package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ExternalLabResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExternalLabResultRepository extends JpaRepository<ExternalLabResult, Long> {

    List<ExternalLabResult> findByPatientIdOrderByResultDateDescIdAsc(Long patientId);

    List<ExternalLabResult> findByEncounterIdOrderByResultDateDescIdAsc(Long encounterId);
}
