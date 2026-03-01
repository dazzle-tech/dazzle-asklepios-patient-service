package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTestReportImageStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link DiagnosticOrderTestReportImageStatusLog}.
 */
public interface DiagnosticOrderTestReportImageStatusLogRepository
        extends JpaRepository<DiagnosticOrderTestReportImageStatusLog, Long> {

    /**
     * Fetch all logs for a given report id (ordered by date desc).
     *
     * @param reportId report id
     * @return logs
     */
    List<DiagnosticOrderTestReportImageStatusLog> findByReportIdOrderByStatusDateDesc(Long reportId);
}
