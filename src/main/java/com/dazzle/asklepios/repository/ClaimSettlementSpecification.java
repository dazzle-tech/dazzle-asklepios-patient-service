package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimSettlement;
import com.dazzle.asklepios.domain.ClaimSettlementInvoice;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementMembershipStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimSettlementStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Database filters for the statements list, so paging happens in the query instead of in memory.
 */
public final class ClaimSettlementSpecification {

    private ClaimSettlementSpecification() {
    }

    public static Specification<ClaimSettlement> of(
            Long payorId,
            String payerNphiesId,
            String settlementNo,
            ClaimSettlementStatus status,
            Instant fromDate,
            Instant toDate,
            boolean submittableOnly
    ) {
        Specification<ClaimSettlement> specification = Specification.where(null);

        specification = andPayor(specification, payorId, payerNphiesId);
        specification = andSettlementNo(specification, settlementNo);
        if (status != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        specification = andCreatedBetween(specification, fromDate, toDate);
        if (submittableOnly) {
            specification = specification.and(submittable());
        }
        return specification;
    }

    /**
     * A statement matches the local payor id or the NPHIES payer id.
     */
    private static Specification<ClaimSettlement> andPayor(
            Specification<ClaimSettlement> specification,
            Long payorId,
            String payerNphiesId
    ) {
        String nphiesId = normalize(payerNphiesId);
        if (payorId == null && nphiesId == null) {
            return specification;
        }
        return specification.and((root, query, cb) -> {
            List<Predicate> matches = new ArrayList<>();
            if (payorId != null) {
                matches.add(cb.equal(root.get("payorId"), payorId));
            }
            if (nphiesId != null) {
                matches.add(cb.equal(cb.lower(root.get("payerNphiesId")), nphiesId));
            }
            return cb.or(matches.toArray(Predicate[]::new));
        });
    }

    private static Specification<ClaimSettlement> andSettlementNo(
            Specification<ClaimSettlement> specification,
            String raw
    ) {
        String value = normalize(raw);
        if (value == null) {
            return specification;
        }
        return specification.and((root, query, cb) -> cb.equal(cb.lower(root.get("settlementNo")), value));
    }

    private static Specification<ClaimSettlement> andCreatedBetween(
            Specification<ClaimSettlement> specification,
            Instant from,
            Instant to
    ) {
        Specification<ClaimSettlement> result = specification;
        if (from != null) {
            result = result.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdDate"), from));
        }
        if (to != null) {
            result = result.and((root, query, cb) -> cb.lessThan(root.get("createdDate"), to));
        }
        return result;
    }

    /**
     * Same rule as the summary's submittable flag: not cancelled and at least one
     * active invoice that has no claim yet.
     */
    private static Specification<ClaimSettlement> submittable() {
        return (root, query, cb) -> {
            Subquery<Long> pendingLines = query.subquery(Long.class);
            Root<ClaimSettlementInvoice> line = pendingLines.from(ClaimSettlementInvoice.class);
            pendingLines.select(line.get("id"))
                    .where(
                            cb.equal(line.get("settlementId"), root.get("id")),
                            cb.equal(line.get("membershipStatus"), ClaimSettlementMembershipStatus.ACTIVE),
                            cb.isNull(line.get("claimRequestId"))
                    );
            return cb.and(
                    cb.notEqual(root.get("status"), ClaimSettlementStatus.CANCELLED),
                    cb.exists(pendingLines)
            );
        };
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase();
        return value.isEmpty() ? null : value;
    }
}
