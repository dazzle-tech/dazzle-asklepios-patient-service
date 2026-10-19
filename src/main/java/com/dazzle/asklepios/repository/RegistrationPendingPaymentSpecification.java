package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class RegistrationPendingPaymentSpecification {

    private static final Set<ServiceSource> INCLUDED_SOURCES = EnumSet.of(
            ServiceSource.LABORATORY,
            ServiceSource.RADIOLOGY,
            ServiceSource.SERVICE_AND_PRODUCT
    );

    private static final Set<PaymentStatus> EXCLUDED_STATUSES = EnumSet.of(
            PaymentStatus.CANCELLED,
            PaymentStatus.EXEMPTED,
            PaymentStatus.PAID
    );

    private RegistrationPendingPaymentSpecification() {
    }

    public static Specification<PatientServiceAndProduct> forFacility(Long facilityId) {
        return Specification
                .where(facilityIdEquals(facilityId))
                .and(serviceSourceIn(INCLUDED_SOURCES))
                .and(paymentStatusNotIn(EXCLUDED_STATUSES))
                .and(notConfirmedFullyInsuredZeroPatient())
                .and(pendingPaymentOrAwaitingFinancialAmounts());
    }

    public static Specification<PatientServiceAndProduct> facilityIdEquals(Long facilityId) {
        return (root, query, cb) -> {
            var subquery = query.subquery(Long.class);
            var encounter = subquery.from(PatientEncounter.class);
            subquery.select(encounter.get("id"));
            subquery.where(cb.equal(encounter.get("facilityId"), facilityId));
            return root.get("encounterId").in(subquery);
        };
    }

    public static Specification<PatientServiceAndProduct> patientNameContains(
            String patientName
    ) {
        return (root, query, cb) -> {
            if (patientName == null || patientName.isBlank()) {
                return cb.conjunction();
            }

            String[] tokens = patientName.trim().toLowerCase().split("\\s+");

            var subquery = query.subquery(Long.class);
            var patient = subquery.from(Patient.class);
            subquery.select(patient.get("id"));

            Expression<String> first =
                    cb.lower(cb.coalesce(patient.get("firstName"), ""));
            Expression<String> second =
                    cb.lower(cb.coalesce(patient.get("secondName"), ""));
            Expression<String> third =
                    cb.lower(cb.coalesce(patient.get("thirdName"), ""));
            Expression<String> last =
                    cb.lower(cb.coalesce(patient.get("lastName"), ""));

            List<Predicate> tokenPredicates = new ArrayList<>();
            for (String token : tokens) {
                if (token == null || token.isBlank()) {
                    continue;
                }
                String like = "%" + token + "%";
                tokenPredicates.add(
                        cb.or(
                                cb.like(first, like),
                                cb.like(second, like),
                                cb.like(third, like),
                                cb.like(last, like)
                        )
                );
            }

            if (tokenPredicates.isEmpty()) {
                return cb.conjunction();
            }

            subquery.where(cb.and(tokenPredicates.toArray(new Predicate[0])));
            return root.get("patientId").in(subquery);
        };
    }

    public static Specification<PatientServiceAndProduct> serviceSourceIn(
            Collection<ServiceSource> sources
    ) {
        return (root, query, cb) -> root.get("serviceSource").in(sources);
    }

    public static Specification<PatientServiceAndProduct> paymentStatusNotIn(
            Collection<PaymentStatus> statuses
    ) {
        return (root, query, cb) -> cb.not(root.get("paymentStatus").in(statuses));
    }

    public static Specification<PatientServiceAndProduct> idGreaterThan(Long afterId) {
        return (root, query, cb) -> cb.greaterThan(root.get("id"), afterId);
    }

    /**
     * Exclude confirmed fully-insured items with zero patient share.
     */
    public static Specification<PatientServiceAndProduct> notConfirmedFullyInsuredZeroPatient() {
        return (root, query, cb) -> cb.not(
                cb.and(
                        cb.equal(root.get("patientShareAmount"), BigDecimal.ZERO),
                        cb.equal(root.get("remainingAmount"), BigDecimal.ZERO),
                        cb.greaterThan(root.get("insuranceShareAmount"), BigDecimal.ZERO)
                )
        );
    }

    /**
     * Unpaid / partially unpaid patient share, pre-auth pending, or unknown financial amounts.
     */
    public static Specification<PatientServiceAndProduct> pendingPaymentOrAwaitingFinancialAmounts() {
        return (root, query, cb) -> cb.or(
                cb.greaterThan(root.get("remainingAmount"), BigDecimal.ZERO),
                cb.equal(
                        root.get("paymentStatus"),
                        PaymentStatus.SKIPPED_PENDING_PRE_AUTH
                ),
                cb.equal(
                        root.get("paymentStatus"),
                        PaymentStatus.INSURANCE_PENDING
                ),
                cb.and(
                        cb.equal(root.get("paymentStatus"), PaymentStatus.PENDING),
                        cb.equal(root.get("unitPrice"), BigDecimal.ZERO),
                        cb.equal(root.get("netAmount"), BigDecimal.ZERO),
                        cb.equal(root.get("patientShareAmount"), BigDecimal.ZERO),
                        cb.equal(root.get("insuranceShareAmount"), BigDecimal.ZERO),
                        cb.equal(root.get("remainingAmount"), BigDecimal.ZERO)
                )
        );
    }
}
