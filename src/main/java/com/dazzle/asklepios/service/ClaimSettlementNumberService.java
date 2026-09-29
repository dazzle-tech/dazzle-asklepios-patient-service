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

/**
 * Settlement numbers are reserved only for accepted claims.
 * Claims from the same upload share one number. Rejected and failed claims keep none.
 */
@Service
@RequiredArgsConstructor
public class ClaimSettlementNumberService {

    private static final Logger LOG = LoggerFactory.getLogger(ClaimSettlementNumberService.class);

    private final EntityManager entityManager;
    private final ClaimRequestRepository claimRequestRepository;

    @Transactional
    public void syncSettlementNo(ClaimRequest claim) {
        if (claim == null) {
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
        claim.setSettlementNo(shared != null ? shared : nextSettlementNo());
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

    private String nextSettlementNo() {
        Number value = (Number) entityManager
                .createNativeQuery("SELECT nextval('claim_settlement_seq')")
                .getSingleResult();
        String settlementNo = "SET-" + value.longValue();
        LOG.info("[CLAIM_SETTLEMENT] Allocated settlementNo={}", settlementNo);
        return settlementNo;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
