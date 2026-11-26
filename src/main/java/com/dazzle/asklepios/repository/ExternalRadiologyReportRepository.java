package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ExternalRadiologyReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExternalRadiologyReportRepository extends JpaRepository<ExternalRadiologyReport, Long> {

    List<ExternalRadiologyReport> findByPatientIdOrderByReportDateDescIdAsc(Long patientId);

    List<ExternalRadiologyReport> findByEncounterIdOrderByReportDateDescIdAsc(Long encounterId);
}
