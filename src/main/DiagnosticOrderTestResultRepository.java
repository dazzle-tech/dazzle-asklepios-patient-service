// src/main/java/com/dazzle/asklepios/repository/DiagnosticOrderTestResultRepository.java
package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@Repository
public interface DiagnosticOrderTestResultRepository extends JpaRepository<DiagnosticOrderTestResult, Long>,
        JpaSpecificationExecutor<DiagnosticOrderTestResult> {

    Page<DiagnosticOrderTestResult> findByOrderId(Long orderId, Pageable pageable);

    Page<DiagnosticOrderTestResult> findByOrderTestId(Long orderTestId, Pageable pageable);

    Page<DiagnosticOrderTestResult> findByProfileTestId(Long profileTestId, Pageable pageable);
}
