
package com.dazzle.asklepios.repository;


import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiagnosticOrderTestResultRepository extends JpaRepository<DiagnosticOrderTestResult, Long>,
        JpaSpecificationExecutor<DiagnosticOrderTestResult> {

    Page<DiagnosticOrderTestResult> findByOrderId(Long orderId, Pageable pageable);

    Page<DiagnosticOrderTestResult> findByOrderTestId(Long orderTestId, Pageable pageable);

    Page<DiagnosticOrderTestResult> findByProfileTestId(Long profileTestId, Pageable pageable);
   void  deleteAllByOrderTestId(Long orderTestId);
    boolean existsByOrderTestId(Long orderTestId);
    @Query("""
    select r.processingStatus
      from DiagnosticOrderTestResult r
     where r.orderTestId = ?1
       and (r.processingStatus is null or r.processingStatus <> com.dazzle.asklepios.domain.enumeration.DiagnosticStatus.CANCELLED)
""")
    List<DiagnosticStatus> findProcessingStatusesByOrderTestId(Long orderTestId);

}

