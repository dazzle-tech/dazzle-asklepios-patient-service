package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface DiagnosticOrderTestReportRepository
        extends JpaRepository<DiagnosticOrderTestReport, Long>,
        JpaSpecificationExecutor<DiagnosticOrderTestReport> {

    Optional<DiagnosticOrderTestReport> findByOrderTestId(Long orderTestId);

}
