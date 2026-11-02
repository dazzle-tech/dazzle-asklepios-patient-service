package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimSettlementLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaimSettlementLogRepository extends JpaRepository<ClaimSettlementLog, Long> {

    List<ClaimSettlementLog> findByFinancialDocumentIdOrderByCreatedDateAscIdAsc(Long financialDocumentId);
}
