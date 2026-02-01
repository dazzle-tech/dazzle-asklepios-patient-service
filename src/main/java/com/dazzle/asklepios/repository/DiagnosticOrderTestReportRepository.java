package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiagnosticOrderTestReportRepository extends JpaRepository<DiagnosticOrderTestReport, Long> {
    Optional<DiagnosticOrderTestReport> findByOrderTestId(Long orderTestId);
    Optional<DiagnosticOrderTestReport> findByOrderIdAndOrderTestId(Long orderId, Long orderTestId);
}