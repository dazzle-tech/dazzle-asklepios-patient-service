package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticTestRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DiagnosticTestRequestRepository
        extends JpaRepository<DiagnosticTestRequest, Long>, JpaSpecificationExecutor<DiagnosticTestRequest> {
}
