package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReportComments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DiagnosticOrderTestReportCommentsRepository
        extends JpaRepository<DiagnosticOrderTestReportComments, Long> {

    List<DiagnosticOrderTestReportComments> findByReportId(Long reportId);
    @Query("""
    select distinct c.reportId
    from DiagnosticOrderTestReportComments c
    where c.reportId in :reportIds
""")
    List<Long> findDistinctReportIdByReportIdIn(List<Long> reportIds);
}
