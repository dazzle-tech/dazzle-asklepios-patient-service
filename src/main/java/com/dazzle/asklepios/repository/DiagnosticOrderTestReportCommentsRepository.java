package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReportComments;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiagnosticOrderTestReportCommentsRepository
        extends JpaRepository<DiagnosticOrderTestReportComments, Long> {

    List<DiagnosticOrderTestReportComments> findByOrderTestId(Long orderTestId);
    List<DiagnosticOrderTestReportComments> findByReportId(Long reportId);



}
