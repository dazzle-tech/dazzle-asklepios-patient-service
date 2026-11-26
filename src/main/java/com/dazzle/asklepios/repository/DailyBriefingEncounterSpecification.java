package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BedTransaction;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.BedTransactionType;
import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.service.dailybriefing.BriefingScope;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public final class DailyBriefingEncounterSpecification {

    private DailyBriefingEncounterSpecification() {
    }

    public static Specification<PatientEncounter> forScope(BriefingScope scope) {
        Specification<PatientEncounter> specification = facilityId(scope.facilityId());
        if (scope.departmentId() != null) {
            specification = specification.and(departmentId(scope.departmentId()));
        }
        if (scope.practitionerId() != null) {
            specification = specification.and(practitionerId(scope.practitionerId()));
        }
        if (scope.practitionerIds() != null) {
            specification = specification.and(practitionerIdIn(scope.practitionerIds()));
        }
        return specification;
    }

    public static Specification<PatientEncounter> withPatientAndAppointment() {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("patient", JoinType.INNER);
                root.fetch("appointment", JoinType.INNER);
                query.distinct(true);
            }
            return cb.conjunction();
        };
    }

    public static Specification<PatientEncounter> withPatient() {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("patient", JoinType.INNER);
                query.distinct(true);
            }
            return cb.conjunction();
        };
    }

    public static Specification<PatientEncounter> facilityId(Long facilityId) {
        return (root, query, cb) -> cb.equal(root.get("facilityId"), facilityId);
    }

    public static Specification<PatientEncounter> departmentId(Long departmentId) {
        return (root, query, cb) -> cb.equal(root.get("departmentId"), departmentId);
    }

    public static Specification<PatientEncounter> practitionerId(Long practitionerId) {
        return (root, query, cb) -> cb.equal(root.get("practitionerId"), practitionerId);
    }

    public static Specification<PatientEncounter> practitionerIdIn(Collection<Long> practitionerIds) {
        return (root, query, cb) -> root.get("practitionerId").in(practitionerIds);
    }

    public static Specification<PatientEncounter> encounterDate(LocalDate day) {
        return (root, query, cb) -> cb.equal(root.get("encounterDate"), day);
    }

    public static Specification<PatientEncounter> encounterDateBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.between(root.get("encounterDate"), from, to);
    }

    public static Specification<PatientEncounter> statusIn(Collection<TreatmentStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<PatientEncounter> statusNotIn(Collection<TreatmentStatus> statuses) {
        return (root, query, cb) -> cb.not(root.get("status").in(statuses));
    }

    public static Specification<PatientEncounter> statusEquals(TreatmentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<PatientEncounter> encounterType(EncounterType type) {
        return (root, query, cb) -> cb.equal(root.get("encounterType"), type);
    }

    public static Specification<PatientEncounter> billingStatus(EncounterBillingStatus billingStatus) {
        return (root, query, cb) -> cb.equal(root.get("billingStatus"), billingStatus);
    }

    public static Specification<PatientEncounter> missingChiefComplaint() {
        return (root, query, cb) -> cb.or(cb.isNull(root.get("chiefComplaint")), cb.equal(root.get("chiefComplaint"), ""));
    }

    public static Specification<PatientEncounter> openOlderThan(Instant cutoff, LocalDate oldestDay) {
        return (root, query, cb) -> cb.or(
                cb.and(cb.isNotNull(root.get("startedDate")), cb.lessThan(root.get("startedDate"), cutoff)),
                cb.and(cb.isNull(root.get("startedDate")), cb.lessThanOrEqualTo(root.get("encounterDate"), oldestDay))
        );
    }

    public static Specification<PatientEncounter> dischargeBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("dischargeAt"), start),
                cb.lessThan(root.get("dischargeAt"), end)
        );
    }

    public static Specification<PatientEncounter> hasBedAssignBetween(Instant start, Instant end) {
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            var bed = subquery.from(BedTransaction.class);
            subquery.select(bed.get("id"));
            subquery.where(
                    cb.equal(bed.get("encounter"), root),
                    cb.equal(bed.get("transactionType"), BedTransactionType.ASSIGN),
                    cb.greaterThanOrEqualTo(bed.get("transactionDate"), start),
                    cb.lessThan(bed.get("transactionDate"), end)
            );
            return cb.exists(subquery);
        };
    }

    public static Specification<PatientEncounter> practitionerPresent() {
        return (root, query, cb) -> cb.isNotNull(root.get("practitionerId"));
    }
}
