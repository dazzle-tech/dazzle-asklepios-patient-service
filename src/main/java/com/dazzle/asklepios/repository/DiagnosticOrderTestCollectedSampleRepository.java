package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTestCollectedSample;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiagnosticOrderTestCollectedSampleRepository
        extends JpaRepository<DiagnosticOrderTestCollectedSample, Long> {

    Page<DiagnosticOrderTestCollectedSample> findByOrderTestId(Long orderTestId, Pageable pageable);

    Page<DiagnosticOrderTestCollectedSample> findByOrderId(Long orderId, Pageable pageable);
    Optional<DiagnosticOrderTestCollectedSample>
    findTopByOrderTestIdOrderByCreatedDateDescIdDesc(Long orderTestId);
}
