package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A statement number is chosen when invoices are grouped, before Waseel submission.
 * Claims that were accepted before that flow still receive a number here, and their stored numbers are not cleared.
 * Rejected claims that belong to a statement are released by {@link ClaimSettlementMembershipService}.
 */
@Service
@RequiredArgsConstructor
public class ClaimSettlementNumberService {

    private static final Logger LOG = LoggerFactory.getLogger(ClaimSettlementNumberService.class);

    private final EntityManager entityManager;
    private final ClaimRequestRepository claimRequestRepository;
    private final ClaimSettlementMembershipService claimSettlementMembershipService;

    /**
     * Keeps a grouped claim on its statement, or applies the older accept-only numbering when the claim was never grouped.
     */
    public void applyAfterRefresh(ClaimRequest claim, boolean assignLegacyIfAccepted) {
        if (claim == null) {
            return;
        }
        if (claimSettlementMembershipService.reconcile(claim)) {
            return;
        }
        if (!assignLegacyIfAccepted) {
            if (claim.getStatus() != ClaimStatus.ACCEPTED) {
                claim.setSettlementNo(null);
            }
            return;
        }
        syncSettlementNo(claim);
    }

    public void applyOutcome(ClaimRequest claim) {
        applyAfterRefresh(claim, false);
    }

    @Transactional
    public void syncSettlementNo(ClaimRequest claim) {
        if (claim == null || claimSettlementMembershipService.isOpenGroupClaim(claim)) {
            return;
        }
        if (claim.getStatus() != ClaimStatus.ACCEPTED) {
            claim.setSettlementNo(null);
            return;
        }
        if (hasText(claim.getSettlementNo())) {
            return;
        }

        String shared = sharedSettlementNo(claim);
        claim.setSettlementNo(shared != null ? shared : allocateSettlementNo());
    }

    /**
     * One settlement number for every accepted claim in this batch that does not already have one.
     * Invoices already placed in a statement keep that statement number.
     */
    @Transactional
    public void assignSharedSettlement(List<ClaimRequest> claims) {
        if (claims == null || claims.isEmpty()) {
            return;
        }

        List<ClaimRequest> needNumber = claims.stream()
                .filter(claim -> claim != null && claim.getId() != null)
                .filter(claim -> claim.getStatus() == ClaimStatus.ACCEPTED)
                .filter(claim -> !hasText(claim.getSettlementNo()))
                .filter(claim -> !claimSettlementMembershipService.isOpenGroupClaim(claim))
                .toList();
        if (needNumber.isEmpty()) {
            return;
        }

        String settlementNo = allocateSettlementNo();
        List<ClaimRequest> stored = claimRequestRepository.findAllById(
                needNumber.stream().map(ClaimRequest::getId).toList()
        );
        for (ClaimRequest claim : stored) {
            if (claim.getStatus() == ClaimStatus.ACCEPTED
                    && !hasText(claim.getSettlementNo())
                    && !claimSettlementMembershipService.isOpenGroupClaim(claim)) {
                claim.setSettlementNo(settlementNo);
            }
        }
        claimRequestRepository.saveAll(stored);

        for (ClaimRequest claim : needNumber) {
            claim.setSettlementNo(settlementNo);
        }
    }

    @Transactional
    public String allocateSettlementNo() {
        Number value = (Number) entityManager
                .createNativeQuery("SELECT nextval('claim_settlement_seq')")
                .getSingleResult();
        String settlementNo = "SET-" + value.longValue();
        LOG.info("[CLAIM_SETTLEMENT] Allocated settlementNo={}", settlementNo);
        return settlementNo;
    }

    private String sharedSettlementNo(ClaimRequest claim) {
        if (!hasText(claim.getUploadName()) || claim.getId() == null) {
            return null;
        }

        return claimRequestRepository.findByUploadName(claim.getUploadName()).stream()
                .filter(other -> other.getId() != null && !other.getId().equals(claim.getId()))
                .filter(other -> other.getStatus() == ClaimStatus.ACCEPTED)
                .map(ClaimRequest::getSettlementNo)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
