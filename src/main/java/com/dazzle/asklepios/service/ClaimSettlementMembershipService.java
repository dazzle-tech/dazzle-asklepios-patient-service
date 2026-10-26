package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.ClaimSettlement;
import com.dazzle.asklepios.domain.ClaimSettlementInvoice;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementMembershipStatus;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
import com.dazzle.asklepios.repository.ClaimSettlementInvoiceRepository;
import com.dazzle.asklepios.repository.ClaimSettlementRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns statement membership. Claims that were submitted before this table existed are left untouched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ClaimSettlementMembershipService {

    private static final String ENTITY_NAME = "claimSettlement";

    private static final ClaimSettlementMembershipStatus ACTIVE =
            ClaimSettlementMembershipStatus.ACTIVE;

    private static final ClaimSettlementMembershipStatus RELEASED =
            ClaimSettlementMembershipStatus.RELEASED;

    private final ClaimSettlementRepository claimSettlementRepository;
    private final ClaimSettlementInvoiceRepository claimSettlementInvoiceRepository;
    private final ClaimRequestRepository claimRequestRepository;
    private final FinancialDocumentRepository financialDocumentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final AtomicBoolean tablesReady = new AtomicBoolean(false);
    private final AtomicBoolean missingTablesWarned = new AtomicBoolean(false);

    /**
     * Old claims keep working when the new tables have not been created yet.
     * A failed probe runs in its own transaction so it does not abort the claim the user is refreshing.
     */
    public boolean tablesReady() {
        if (tablesReady.get()) {
            return true;
        }

        TransactionTemplate probe = new TransactionTemplate(transactionManager);
        probe.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        probe.setReadOnly(true);
        try {
            probe.executeWithoutResult(status ->
                    jdbcTemplate.queryForList("SELECT 1 FROM claim_settlement_invoice WHERE 1 = 0")
            );
            tablesReady.set(true);
            return true;
        } catch (DataAccessException ex) {
            if (missingTablesWarned.compareAndSet(false, true)) {
                log.warn(
                        "[CLAIM_SETTLEMENT] claim_settlement tables are not installed. "
                                + "Existing claims stay unchanged."
                );
            }
            return false;
        }
    }

    @Transactional(readOnly = true)
    public Set<Long> activeDocumentIds(Collection<Long> documentIds) {
        if (!tablesReady() || documentIds == null || documentIds.isEmpty()) {
            return Set.of();
        }

        return claimSettlementInvoiceRepository
                .findByFinancialDocumentIdInAndMembershipStatus(documentIds, ACTIVE)
                .stream()
                .map(ClaimSettlementInvoice::getFinancialDocumentId)
                .collect(Collectors.toSet());
    }

    public void assertNotInActiveSettlement(Collection<Long> documentIds) {
        if (!tablesReady() || documentIds == null || documentIds.isEmpty()) {
            return;
        }

        List<ClaimSettlementInvoice> lines = claimSettlementInvoiceRepository
                .findByFinancialDocumentIdInAndMembershipStatus(documentIds, ACTIVE);
        if (lines.isEmpty()) {
            return;
        }

        ClaimSettlementInvoice line = lines.get(0);
        throw new BadRequestAlertException(
                "Invoice " + invoiceLabel(line.getFinancialDocumentId())
                        + " is already included in statement "
                        + settlementLabel(line.getSettlementId())
                        + ". Submit that statement.",
                ENTITY_NAME,
                "settlement.alreadyGrouped"
        );
    }

    /**
     * Links the claim that was just opened to the invoice's statement, when the invoice was grouped first.
     * A legacy invoice with no statement line is unchanged.
     */
    public void bindNewClaim(ClaimRequest claim) {
        if (!tablesReady()
                || claim == null
                || claim.getId() == null
                || claim.getFinancialDocumentId() == null) {
            return;
        }

        int updated = claimSettlementInvoiceRepository.bindClaim(
                claim.getFinancialDocumentId(),
                claim.getId(),
                ACTIVE
        );
        if (updated > 0) {
            return;
        }

        boolean blocked = claimSettlementInvoiceRepository
                .findByFinancialDocumentIdAndMembershipStatus(claim.getFinancialDocumentId(), ACTIVE)
                .isPresent();
        if (blocked) {
            throw new BadRequestAlertException(
                    "Invoice " + invoiceLabel(claim.getFinancialDocumentId())
                            + " already has a claim under statement "
                            + settlementLabelForDocument(claim.getFinancialDocumentId())
                            + ".",
                    ENTITY_NAME,
                    "settlement.alreadyLinked"
            );
        }
    }

    /**
     * @return true when this claim belongs to a statement and its number was kept or cleared here.
     *         False means the older accept-then-number rule still applies.
     */
    public boolean reconcile(ClaimRequest claim) {
        if (!tablesReady()
                || claim == null
                || claim.getId() == null
                || claim.getFinancialDocumentId() == null) {
            return false;
        }

        Optional<ClaimSettlementInvoice> active = claimSettlementInvoiceRepository
                .findByFinancialDocumentIdAndMembershipStatus(claim.getFinancialDocumentId(), ACTIVE);
        if (active.isPresent()) {
            ClaimSettlementInvoice line = active.get();
            if (line.getClaimRequestId() != null && !line.getClaimRequestId().equals(claim.getId())) {
                return false;
            }
            if (line.getClaimRequestId() == null) {
                line.setClaimRequestId(claim.getId());
            }
            if (ClaimSettlementRules.leavesSettlement(claim.getStatus())) {
                releaseLine(line, claim, failureReason(claim));
            } else {
                keepLine(line, claim);
            }
            return true;
        }

        if (!ClaimSettlementRules.leavesSettlement(claim.getStatus())) {
            Optional<ClaimSettlementInvoice> released = claimSettlementInvoiceRepository
                    .findFirstByClaimRequestIdAndMembershipStatusOrderByIdDesc(claim.getId(), RELEASED);
            if (released.isPresent() && restoreIfStillFree(released.get(), claim)) {
                return true;
            }
        }

        if (claimSettlementInvoiceRepository.existsByClaimRequestIdAndMembershipStatus(claim.getId(), RELEASED)) {
            claim.setSettlementNo(null);
            return true;
        }
        return false;
    }

    public void releaseClaim(ClaimRequest claim, String reason) {
        if (!tablesReady()
                || claim == null
                || claim.getId() == null
                || claim.getFinancialDocumentId() == null) {
            return;
        }

        claimSettlementInvoiceRepository
                .findByFinancialDocumentIdAndMembershipStatus(claim.getFinancialDocumentId(), ACTIVE)
                .filter(line -> claim.getId().equals(line.getClaimRequestId()))
                .ifPresent(line -> releaseLine(line, claim, reason));
    }

    public void reconcileLinkedClaims(Long settlementId) {
        if (!tablesReady() || settlementId == null) {
            return;
        }
        List<ClaimSettlementInvoice> lines = claimSettlementInvoiceRepository.findBySettlementId(settlementId);
        for (ClaimSettlementInvoice line : lines) {
            if (line.getMembershipStatus() != ACTIVE || line.getClaimRequestId() == null) {
                continue;
            }
            claimRequestRepository.findById(line.getClaimRequestId()).ifPresent(this::reconcile);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> unlinkedActiveDocumentIds(Long settlementId) {
        if (!tablesReady() || settlementId == null) {
            return List.of();
        }
        return claimSettlementInvoiceRepository.findBySettlementId(settlementId).stream()
                .filter(line -> line.getMembershipStatus() == ACTIVE && line.getClaimRequestId() == null)
                .map(ClaimSettlementInvoice::getFinancialDocumentId)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isOpenGroupClaim(ClaimRequest claim) {
        if (!tablesReady() || claim == null || claim.getFinancialDocumentId() == null) {
            return false;
        }

        return claimSettlementInvoiceRepository
                .findByFinancialDocumentIdAndMembershipStatus(claim.getFinancialDocumentId(), ACTIVE)
                .filter(line -> line.getClaimRequestId() == null
                        || line.getClaimRequestId().equals(claim.getId()))
                .isPresent();
    }

    public ClaimSettlement recalculate(Long settlementId) {
        if (!tablesReady() || settlementId == null) {
            return null;
        }
        ClaimSettlement settlement = claimSettlementRepository.findById(settlementId).orElse(null);
        if (settlement == null) {
            return null;
        }

        List<ClaimSettlementInvoice> lines = claimSettlementInvoiceRepository.findBySettlementId(settlementId);
        List<ClaimSettlementInvoice> activeLines = lines.stream()
                .filter(line -> line.getMembershipStatus() == ACTIVE)
                .toList();
        int releasedCount = lines.size() - activeLines.size();

        Map<Long, FinancialDocument> documents = documentsById(activeLines);
        Map<Long, ClaimRequest> claims = claimsById(activeLines);

        BigDecimal invoiceTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal claimTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        int linkedActiveCount = 0;

        for (ClaimSettlementInvoice line : activeLines) {
            FinancialDocument document = documents.get(line.getFinancialDocumentId());
            if (document != null) {
                invoiceTotal = invoiceTotal.add(money(document.getTotalAmount()));
            }

            if (line.getClaimRequestId() == null) {
                continue;
            }
            ClaimRequest claim = claims.get(line.getClaimRequestId());
            if (claim == null || ClaimSettlementRules.leavesSettlement(claim.getStatus())) {
                continue;
            }
            linkedActiveCount++;
            claimTotal = claimTotal.add(money(claim.getTotalNet()));
        }

        settlement.setInvoiceCount(activeLines.size());
        settlement.setTotalInvoiceAmount(invoiceTotal);
        settlement.setTotalClaimAmount(claimTotal);
        settlement.setStatus(ClaimSettlementRules.summarize(
                activeLines.size(),
                releasedCount,
                linkedActiveCount
        ));
        return claimSettlementRepository.save(settlement);
    }

    private boolean restoreIfStillFree(ClaimSettlementInvoice line, ClaimRequest claim) {
        boolean taken = claimSettlementInvoiceRepository
                .findByFinancialDocumentIdAndMembershipStatus(line.getFinancialDocumentId(), ACTIVE)
                .isPresent();
        if (taken) {
            return false;
        }

        line.setMembershipStatus(ACTIVE);
        line.setReleasedAt(null);
        line.setReleaseReason(null);
        keepLine(line, claim);
        log.info(
                "[CLAIM_SETTLEMENT] Restored invoiceId={} claimId={} settlementId={} after a later accepted result",
                line.getFinancialDocumentId(),
                claim.getId(),
                line.getSettlementId()
        );
        return true;
    }

    private void keepLine(ClaimSettlementInvoice line, ClaimRequest claim) {
        ClaimSettlement settlement = claimSettlementRepository.findById(line.getSettlementId()).orElse(null);
        if (settlement != null && hasText(settlement.getSettlementNo())) {
            claim.setSettlementNo(settlement.getSettlementNo());
        }
        claimSettlementInvoiceRepository.saveAndFlush(line);
        recalculate(line.getSettlementId());
    }

    private void releaseLine(ClaimSettlementInvoice line, ClaimRequest claim, String reason) {
        line.setMembershipStatus(RELEASED);
        line.setReleasedAt(Instant.now());
        line.setReleaseReason(limit(reason));
        line.setClaimRequestId(claim.getId());
        claim.setSettlementNo(null);
        claimSettlementInvoiceRepository.saveAndFlush(line);
        recalculate(line.getSettlementId());
        log.info(
                "[CLAIM_SETTLEMENT] Released invoiceId={} claimId={} settlementId={} reason={}",
                line.getFinancialDocumentId(),
                claim.getId(),
                line.getSettlementId(),
                line.getReleaseReason()
        );
    }

    private Map<Long, FinancialDocument> documentsById(List<ClaimSettlementInvoice> lines) {
        Set<Long> documentIds = lines.stream()
                .map(ClaimSettlementInvoice::getFinancialDocumentId)
                .collect(Collectors.toSet());
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return financialDocumentRepository.findAllById(documentIds).stream()
                .collect(Collectors.toMap(FinancialDocument::getId, Function.identity()));
    }

    private Map<Long, ClaimRequest> claimsById(List<ClaimSettlementInvoice> lines) {
        Set<Long> claimIds = lines.stream()
                .map(ClaimSettlementInvoice::getClaimRequestId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (claimIds.isEmpty()) {
            return Map.of();
        }
        return claimRequestRepository.findAllById(claimIds).stream()
                .collect(Collectors.toMap(ClaimRequest::getId, Function.identity()));
    }

    private String invoiceLabel(Long documentId) {
        return financialDocumentRepository.findById(documentId)
                .map(FinancialDocument::getDocumentNumber)
                .filter(this::hasText)
                .map(String::trim)
                .orElse(String.valueOf(documentId));
    }

    private String settlementLabel(Long settlementId) {
        return claimSettlementRepository.findById(settlementId)
                .map(ClaimSettlement::getSettlementNo)
                .filter(this::hasText)
                .orElse(String.valueOf(settlementId));
    }

    private String settlementLabelForDocument(Long documentId) {
        return claimSettlementInvoiceRepository
                .findByFinancialDocumentIdAndMembershipStatus(documentId, ACTIVE)
                .map(line -> settlementLabel(line.getSettlementId()))
                .orElse("the current statement");
    }

    private String failureReason(ClaimRequest claim) {
        if (hasText(claim.getMessage())) {
            return claim.getMessage().trim();
        }
        if (hasText(claim.getOutcome())) {
            return claim.getOutcome().trim();
        }
        return "Claim was not accepted";
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String limit(String value) {
        if (!hasText(value)) {
            return "Claim was not accepted";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 4000 ? trimmed : trimmed.substring(0, 4000);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
