package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.ClaimSettlement;
import com.dazzle.asklepios.domain.ClaimSettlementInvoice;
import com.dazzle.asklepios.domain.ClaimSettlementLog;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementLogAction;
import com.dazzle.asklepios.repository.ClaimSettlementLogRepository;
import com.dazzle.asklepios.repository.ClaimSettlementRepository;
import com.dazzle.asklepios.service.dto.billing.ClaimSettlementLogResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes the statement history of each invoice.
 * When the log table is not installed yet the claim flow continues without history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ClaimSettlementLogService {

    private static final int NOTE_LIMIT = 4000;

    private final ClaimSettlementLogRepository claimSettlementLogRepository;
    private final ClaimSettlementRepository claimSettlementRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final AtomicBoolean tableReady = new AtomicBoolean(false);
    private final AtomicBoolean missingTableWarned = new AtomicBoolean(false);

    public void record(
            ClaimSettlementInvoice line,
            ClaimRequest claim,
            ClaimSettlementLogAction action,
            String note
    ) {
        if (line == null || action == null || !tableReady()) {
            return;
        }

        claimSettlementLogRepository.save(ClaimSettlementLog.builder()
                .settlementId(line.getSettlementId())
                .settlementNo(settlementNo(line.getSettlementId()))
                .financialDocumentId(line.getFinancialDocumentId())
                .claimRequestId(claim == null ? line.getClaimRequestId() : claim.getId())
                .action(action)
                .claimStatus(claim == null ? null : claim.getStatus())
                .note(limit(note))
                .build());
    }

    @Transactional(readOnly = true)
    public List<ClaimSettlementLogResponse> findByInvoice(Long financialDocumentId) {
        if (financialDocumentId == null || !tableReady()) {
            return List.of();
        }
        return claimSettlementLogRepository.findByFinancialDocumentIdOrderByCreatedDateAscIdAsc(financialDocumentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private boolean tableReady() {
        if (tableReady.get()) {
            return true;
        }

        TransactionTemplate probe = new TransactionTemplate(transactionManager);
        probe.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        probe.setReadOnly(true);
        try {
            probe.executeWithoutResult(status ->
                    jdbcTemplate.queryForList("SELECT 1 FROM claim_settlement_log WHERE 1 = 0")
            );
            tableReady.set(true);
            return true;
        } catch (DataAccessException ex) {
            if (missingTableWarned.compareAndSet(false, true)) {
                log.warn("[CLAIM_SETTLEMENT] claim_settlement_log is not installed. Statement history is skipped.");
            }
            return false;
        }
    }

    private String settlementNo(Long settlementId) {
        if (settlementId == null) {
            return null;
        }
        return claimSettlementRepository.findById(settlementId)
                .map(ClaimSettlement::getSettlementNo)
                .orElse(null);
    }

    private ClaimSettlementLogResponse toResponse(ClaimSettlementLog entry) {
        return new ClaimSettlementLogResponse(
                entry.getId(),
                entry.getSettlementId(),
                entry.getSettlementNo(),
                entry.getFinancialDocumentId(),
                entry.getClaimRequestId(),
                entry.getAction(),
                entry.getClaimStatus(),
                entry.getNote(),
                entry.getCreatedDate(),
                entry.getCreatedBy()
        );
    }

    private String limit(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= NOTE_LIMIT ? trimmed : trimmed.substring(0, NOTE_LIMIT);
    }
}
