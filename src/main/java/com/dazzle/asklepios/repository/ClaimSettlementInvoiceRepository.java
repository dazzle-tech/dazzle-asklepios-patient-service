package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimSettlementInvoice;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementMembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClaimSettlementInvoiceRepository extends JpaRepository<ClaimSettlementInvoice, Long> {

    List<ClaimSettlementInvoice> findBySettlementId(Long settlementId);

    List<ClaimSettlementInvoice> findBySettlementIdIn(Collection<Long> settlementIds);

    List<ClaimSettlementInvoice> findByFinancialDocumentIdInAndMembershipStatus(
            Collection<Long> financialDocumentIds,
            ClaimSettlementMembershipStatus membershipStatus
    );

    Optional<ClaimSettlementInvoice> findByFinancialDocumentIdAndMembershipStatus(
            Long financialDocumentId,
            ClaimSettlementMembershipStatus membershipStatus
    );

    Optional<ClaimSettlementInvoice> findFirstByClaimRequestIdAndMembershipStatusOrderByIdDesc(
            Long claimRequestId,
            ClaimSettlementMembershipStatus membershipStatus
    );

    boolean existsByClaimRequestIdAndMembershipStatus(
            Long claimRequestId,
            ClaimSettlementMembershipStatus membershipStatus
    );

    /**
     * Attaches a new claim to the invoice's active statement line.
     * A line already linked to another claim is left unchanged.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE ClaimSettlementInvoice line
               SET line.claimRequestId = :claimId
             WHERE line.financialDocumentId = :documentId
               AND line.membershipStatus = :status
               AND (line.claimRequestId IS NULL OR line.claimRequestId = :claimId)
            """)
    int bindClaim(
            @Param("documentId") Long documentId,
            @Param("claimId") Long claimId,
            @Param("status") ClaimSettlementMembershipStatus status
    );
}
