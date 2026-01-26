package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository for {@link DiagnosticOrderTestResult}.
 *
 * <p>Provides basic CRUD operations and specification execution for advanced filtering.</p>
 */
@Repository
public interface DiagnosticOrderTestResultRepository extends JpaRepository<DiagnosticOrderTestResult, Long>,
        JpaSpecificationExecutor<DiagnosticOrderTestResult> {

    boolean existsByOrderTestId(Long orderTestId);

    /**
     * Returns processing statuses of results for a given test, excluding CANCELLED statuses.
     *
     * @param orderTestId diagnostic order test id
     * @return list of processing statuses (excluding CANCELLED)
     */
    @Query("""
                select r.processingStatus
                  from DiagnosticOrderTestResult r
                 where r.orderTestId = ?1
                   and (r.processingStatus is null
                        or r.processingStatus <> com.dazzle.asklepios.domain.enumeration.DiagnosticStatus.CANCELLED)
            """)
    List<DiagnosticStatus> findProcessingStatusesByOrderTestId(Long orderTestId);
}
