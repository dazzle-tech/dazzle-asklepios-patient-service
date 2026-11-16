package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.BillingRefund;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingRefundStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.service.dailybriefing.BriefingScope;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class DailyBriefingBillingSpecification {

    private DailyBriefingBillingSpecification() {
    }

    public static Specification<BillingCharge> chargesForScope(BriefingScope scope) {
        return (root, query, cb) -> {
            Join<BillingCharge, PatientEncounter> encounter = root.join("encounter", JoinType.INNER);
            return encounterScope(encounter, scope, cb);
        };
    }

    public static Specification<BillingCharge> chargeDateBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("chargeDate"), start),
                cb.lessThan(root.get("chargeDate"), end)
        );
    }

    public static Specification<BillingCharge> cancelledDateBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("cancelledDate"), start),
                cb.lessThan(root.get("cancelledDate"), end)
        );
    }

    public static Specification<BillingCharge> statusNotIn(Collection<BillingChargeStatus> statuses) {
        return (root, query, cb) -> cb.not(root.get("status").in(statuses));
    }

    public static Specification<BillingCharge> statusIn(Collection<BillingChargeStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<BillingCharge> statusEquals(BillingChargeStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<BillingCharge> cancelledDateIsNull() {
        return (root, query, cb) -> cb.isNull(root.get("cancelledDate"));
    }

    public static Specification<BillingCharge> unbilledCompletedOpen(LocalDate day, Collection<TreatmentStatus> completed) {
        return (root, query, cb) -> {
            Join<BillingCharge, PatientEncounter> encounter = root.join("encounter", JoinType.INNER);
            return cb.and(
                    cb.equal(encounter.get("encounterDate"), day),
                    encounter.get("status").in(completed),
                    cb.equal(encounter.get("billingStatus"), EncounterBillingStatus.OPEN)
            );
        };
    }

    public static Specification<BillingPayment> paymentsForScope(BriefingScope scope) {
        return (root, query, cb) -> {
            Join<BillingPayment, PatientEncounter> encounter = root.join("encounter", JoinType.INNER);
            return encounterScope(encounter, scope, cb);
        };
    }

    public static Specification<BillingPayment> paymentDateBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("paymentDate"), start),
                cb.lessThan(root.get("paymentDate"), end)
        );
    }

    public static Specification<BillingPayment> paymentStatus(BillingPaymentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<BillingRefund> refundsForScope(BriefingScope scope) {
        return (root, query, cb) -> {
            Join<BillingRefund, PatientEncounter> encounter = root.join("encounter", JoinType.INNER);
            return encounterScope(encounter, scope, cb);
        };
    }

    public static Specification<BillingRefund> refundCompletedBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("completedDate"), start),
                cb.lessThan(root.get("completedDate"), end)
        );
    }

    public static Specification<BillingRefund> refundStatusIn(Collection<BillingRefundStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<BillingRefund> originalPaymentPresent() {
        return (root, query, cb) -> cb.isNotNull(root.get("originalPayment"));
    }

    public static Specification<BillingRefund> originalPaymentAbsent() {
        return (root, query, cb) -> cb.isNull(root.get("originalPayment"));
    }

    public static Specification<BillingRefund> withOriginalPayment() {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("originalPayment", JoinType.INNER);
                query.distinct(true);
            }
            return cb.isNotNull(root.get("originalPayment"));
        };
    }

    public static Specification<BillingChargeResponsibility> responsibilitiesForScope(BriefingScope scope) {
        return (root, query, cb) -> {
            Join<BillingChargeResponsibility, PatientEncounter> encounter = root.join("encounter", JoinType.INNER);
            return encounterScope(encounter, scope, cb);
        };
    }

    public static Specification<BillingChargeResponsibility> activeResponsibility(Collection<BillingChargeStatus> inactiveCharges) {
        return (root, query, cb) -> {
            Join<BillingChargeResponsibility, BillingCharge> charge = root.join("charge", JoinType.INNER);
            return cb.and(
                    cb.isNull(root.get("supersededResponsibility")),
                    cb.not(charge.get("status").in(inactiveCharges))
            );
        };
    }

    public static Specification<BillingChargeResponsibility> responsibilityChargeDateBetween(Instant start, Instant end) {
        return (root, query, cb) -> {
            Join<BillingChargeResponsibility, BillingCharge> charge = root.join("charge", JoinType.INNER);
            return cb.and(
                    cb.greaterThanOrEqualTo(charge.get("chargeDate"), start),
                    cb.lessThan(charge.get("chargeDate"), end)
            );
        };
    }

    public static Specification<BillingChargeResponsibility> pendingAuthorizationHold(Collection<BillingChargeStatus> inactiveCharges) {
        return (root, query, cb) -> {
            Join<BillingChargeResponsibility, BillingCharge> charge = root.join("charge", JoinType.INNER);
            return cb.and(
                    cb.isTrue(root.get("preAuthorizationRequired")),
                    cb.equal(root.get("preAuthorizationStatus"), PreAuthorizationStatus.PENDING_APPROVAL),
                    cb.isNull(root.get("supersededResponsibility")),
                    cb.not(charge.get("status").in(inactiveCharges))
            );
        };
    }

    public static Specification<BillingChargeResponsibility> withPatientServiceProduct() {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("patientServiceProduct", JoinType.INNER);
                root.fetch("patient", JoinType.INNER);
                root.fetch("encounter", JoinType.INNER);
                query.distinct(true);
            }
            return cb.conjunction();
        };
    }

    public static Specification<FinancialDocument> issuedInvoices(BriefingScope scope, Instant start, Instant end) {
        return (root, query, cb) -> {
            Subquery<Long> encounters = query.subquery(Long.class);
            Root<PatientEncounter> encounter = encounters.from(PatientEncounter.class);
            encounters.select(encounter.get("id"));
            List<Predicate> scopePredicates = new ArrayList<>();
            scopePredicates.add(cb.equal(encounter.get("id"), root.get("encounterId")));
            scopePredicates.add(cb.equal(encounter.get("facilityId"), scope.facilityId()));
            if (scope.departmentId() != null) {
                scopePredicates.add(cb.equal(encounter.get("departmentId"), scope.departmentId()));
            }
            if (scope.practitionerId() != null) {
                scopePredicates.add(cb.equal(encounter.get("practitionerId"), scope.practitionerId()));
            }
            if (scope.practitionerIds() != null) {
                scopePredicates.add(encounter.get("practitionerId").in(scope.practitionerIds()));
            }
            encounters.where(scopePredicates.toArray(Predicate[]::new));
            return cb.and(
                    cb.equal(root.get("documentType"), FinancialDocumentType.INVOICE),
                    cb.not(root.get("status").in(List.of(FinancialDocumentStatus.DRAFT, FinancialDocumentStatus.CANCELLED))),
                    cb.greaterThanOrEqualTo(root.get("createdDate"), start),
                    cb.lessThan(root.get("createdDate"), end),
                    cb.exists(encounters)
            );
        };
    }

    private static Predicate encounterScope(
            Join<?, PatientEncounter> encounter,
            BriefingScope scope,
            jakarta.persistence.criteria.CriteriaBuilder cb
    ) {
        List<Predicate> predicates = new ArrayList<>();
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
        return cb.and(predicates.toArray(Predicate[]::new));
    }
}
