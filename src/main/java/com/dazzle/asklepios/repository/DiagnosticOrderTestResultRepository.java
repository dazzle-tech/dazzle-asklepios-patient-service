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

    /**
     * Returns processing statuses of results for a given test, excluding CANCELLED statuses.
     *
     * @param orderTestId diagnostic order test id

     */

    List<DiagnosticStatus> findProcessingStatusByOrderTestIdAndProcessingStatusNot( Long orderTestId, DiagnosticStatus status );


    List<Long> findDistinctProfileTestIdByOrderTestId(Long orderTestId);

}
