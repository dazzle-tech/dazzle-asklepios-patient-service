package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.service.dailybriefing.BriefingScope;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class DailyBriefingClaimSpecification {

    private DailyBriefingClaimSpecification() {
    }

    public static Specification<ClaimRequest> forScope(BriefingScope scope) {
        return (root, query, cb) -> encounterExists(root.get("encounterId"), scope, query, cb);
    }

    public static Specification<ClaimRequest> statusEquals(ClaimStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<ClaimRequest> submittedBetween(Instant start, Instant end, Collection<ClaimStatus> statuses) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("submittedAt"), start),
                cb.lessThan(root.get("submittedAt"), end),
                root.get("status").in(statuses)
        );
    }

    public static Specification<PreAuthorizationRequest> authorizationsForScope(BriefingScope scope) {
        return (root, query, cb) -> cb.and(
                cb.isFalse(root.get("isCancelled")),
                encounterExists(root.get("encounterId"), scope, query, cb)
        );
    }

    public static Specification<DiagnosticOrderTest> testsForScope(BriefingScope scope) {
        return (root, query, cb) -> {
            Subquery<Long> orders = query.subquery(Long.class);
            Root<DiagnosticOrder> order = orders.from(DiagnosticOrder.class);
            orders.select(order.get("id"));
            orders.where(
                    cb.equal(order.get("id"), root.get("orderId")),
                    encounterExists(order.get("encounterId"), scope, orders, cb)
            );
            return cb.exists(orders);
        };
    }

    public static Specification<DiagnosticOrderTest> processingStatusNotIn(Collection<DiagnosticStatus> statuses) {
        return (root, query, cb) -> cb.not(root.get("processingStatus").in(statuses));
    }

    public static Specification<DiagnosticOrderTest> createdBefore(Instant cutoff) {
        return (root, query, cb) -> cb.and(cb.isNotNull(root.get("createdDate")), cb.lessThan(root.get("createdDate"), cutoff));
    }

    public static Specification<DiagnosticOrderTest> createdOnOrAfter(Instant cutoff) {
        return (root, query, cb) -> cb.and(cb.isNotNull(root.get("createdDate")), cb.greaterThanOrEqualTo(root.get("createdDate"), cutoff));
    }

    public static Specification<DiagnosticOrderTest> createdDateMissing() {
        return (root, query, cb) -> cb.isNull(root.get("createdDate"));
    }

    public static Specification<DiagnosticOrderTest> turnaroundCompletedBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.or(
                cb.and(cb.isNotNull(root.get("approvedDate")), cb.greaterThanOrEqualTo(root.get("approvedDate"), start), cb.lessThan(root.get("approvedDate"), end)),
                cb.and(cb.isNull(root.get("approvedDate")), cb.isNotNull(root.get("readyDate")), cb.greaterThanOrEqualTo(root.get("readyDate"), start), cb.lessThan(root.get("readyDate"), end))
        );
    }

    private static Predicate encounterExists(
            Path<Long> encounterId,
            BriefingScope scope,
            jakarta.persistence.criteria.AbstractQuery<?> query,
            CriteriaBuilder cb
    ) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<PatientEncounter> encounter = subquery.from(PatientEncounter.class);
        subquery.select(encounter.get("id"));
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(encounter.get("id"), encounterId));
        predicates.add(cb.equal(encounter.get("facilityId"), scope.facilityId()));
        if (scope.departmentId() != null) {
            predicates.add(cb.equal(encounter.get("departmentId"), scope.departmentId()));
        }
        if (scope.practitionerId() != null) {
            predicates.add(cb.equal(encounter.get("practitionerId"), scope.practitionerId()));
        }
        if (scope.practitionerIds() != null) {
            predicates.add(encounter.get("practitionerId").in(scope.practitionerIds()));
        }
        subquery.where(predicates.toArray(Predicate[]::new));
        return cb.exists(subquery);
    }
}
