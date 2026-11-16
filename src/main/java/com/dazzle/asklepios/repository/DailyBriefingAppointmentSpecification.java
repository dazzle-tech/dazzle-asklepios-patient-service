package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.service.dailybriefing.BriefingScope;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class DailyBriefingAppointmentSpecification {

    private DailyBriefingAppointmentSpecification() {
    }

    public static Specification<Appointment> forScope(BriefingScope scope) {
        Specification<Appointment> specification = facilityId(scope.facilityId());
        if (scope.departmentId() != null) {
            specification = specification.and(departmentId(scope.departmentId()));
        }
        if (scope.practitionerId() != null) {
            specification = specification.and(practitioner(scope.practitionerId()));
        }
        if (scope.practitionerIds() != null) {
            specification = specification.and(practitionerIn(scope.practitionerIds()));
        }
        return specification;
    }

    public static Specification<Appointment> withPatient() {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("patient", JoinType.INNER);
                query.distinct(true);
            }
            return cb.isNotNull(root.get("patient"));
        };
    }

    public static Specification<Appointment> facilityId(Long facilityId) {
        return (root, query, cb) -> cb.equal(root.get("facilityId"), facilityId);
    }

    public static Specification<Appointment> departmentId(Long departmentId) {
        return (root, query, cb) -> cb.equal(root.get("departmentId"), departmentId);
    }

    public static Specification<Appointment> practitioner(Long practitionerId) {
        return (root, query, cb) -> cb.or(
                cb.equal(root.get("defaultPractitionerId"), practitionerId),
                cb.and(
                        cb.equal(root.get("resourceType"), TemplateType.PRACTITIONER),
                        cb.equal(root.get("resourceId"), practitionerId)
                )
        );
    }

    public static Specification<Appointment> practitionerIn(Collection<Long> practitionerIds) {
        return (root, query, cb) -> cb.or(
                root.get("defaultPractitionerId").in(practitionerIds),
                cb.and(
                        cb.equal(root.get("resourceType"), TemplateType.PRACTITIONER),
                        root.get("resourceId").in(practitionerIds)
                )
        );
    }

    public static Specification<Appointment> startBetween(Instant start, Instant end) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("startDatetime"), start),
                cb.lessThan(root.get("startDatetime"), end)
        );
    }

    public static Specification<Appointment> patientPresent() {
        return (root, query, cb) -> cb.isNotNull(root.get("patient"));
    }

    public static Specification<Appointment> patientAbsent() {
        return (root, query, cb) -> cb.isNull(root.get("patient"));
    }

    public static Specification<Appointment> statusEquals(AppointmentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Appointment> statusNotIn(Collection<AppointmentStatus> statuses) {
        return (root, query, cb) -> cb.not(root.get("status").in(statuses));
    }

    public static Specification<Appointment> bookedWithoutPractitioner() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("patient")));
            predicates.add(cb.isTrue(root.get("requirePractitioner")));
            predicates.add(cb.isNull(root.get("defaultPractitionerId")));
            predicates.add(cb.or(
                    cb.notEqual(root.get("resourceType"), TemplateType.PRACTITIONER),
                    cb.isNull(root.get("resourceId"))
            ));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<Appointment> defaultPractitionerPresent() {
        return (root, query, cb) -> cb.isNotNull(root.get("defaultPractitionerId"));
    }

    public static Specification<Appointment> resourceIsPractitioner() {
        return (root, query, cb) -> cb.equal(root.get("resourceType"), TemplateType.PRACTITIONER);
    }
}
