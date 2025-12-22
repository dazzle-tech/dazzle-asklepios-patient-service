// src/main/java/com/dazzle/asklepios/repository/DiagnosticOrderTestRepository.java
package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface DiagnosticOrderTestRepository extends JpaRepository<DiagnosticOrderTest, Long> {

    Page<DiagnosticOrderTest> findByOrderId(Long orderId, Pageable pageable);

    Page<DiagnosticOrderTest> findByOrderIdAndStatus(Long orderId, String status, Pageable pageable);

    Page<DiagnosticOrderTest> findByOrderIdAndStatusNotIn(Long orderId, Collection<String> statuses, Pageable pageable);
}
