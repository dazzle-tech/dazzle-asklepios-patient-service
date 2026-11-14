package com.dazzle.asklepios.integration.nahdi.repository;

import com.dazzle.asklepios.integration.nahdi.domain.NahdiIntegrationLog;
import com.dazzle.asklepios.integration.nahdi.domain.enumeration.NahdiIntegrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NahdiIntegrationLogRepository extends JpaRepository<NahdiIntegrationLog, Long> {

    Page<NahdiIntegrationLog> findByStatus(NahdiIntegrationStatus status, Pageable pageable);
}
