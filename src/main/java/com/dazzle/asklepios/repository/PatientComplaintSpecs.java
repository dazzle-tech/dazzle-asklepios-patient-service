package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientComplaint;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class PatientComplaintSpecs {

    private PatientComplaintSpecs() {}

    public static Specification<PatientComplaint> withFilters(
        Long facilityId,
        String search,
        PatientComplaintStatus status,
        Long assignedUserId,
        Instant receivedFrom,
        Instant receivedTo,
        String summaryFilter,
        Long currentUserId,
        Instant overdueBefore
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("facilityId"), facilityId));

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("complaintReference")), pattern),
                        cb.like(cb.lower(root.get("patientName")), pattern),
                        cb.like(cb.lower(root.get("subject")), pattern)
                    )
                );
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (assignedUserId != null) {
                predicates.add(cb.equal(root.get("assignedUserId"), assignedUserId));
            }

            if (receivedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("receivedDate"), receivedFrom));
            }

            if (receivedTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("receivedDate"), receivedTo));
            }

            if (summaryFilter != null && !summaryFilter.isBlank()) {
                switch (summaryFilter.trim().toUpperCase()) {
                    case "NEW" -> predicates.add(cb.equal(root.get("status"), PatientComplaintStatus.NEW));
                    case "UNASSIGNED" -> predicates.add(cb.isNull(root.get("assignedUserId")));
                    case "ASSIGNED_TO_ME" -> {
                        if (currentUserId != null) {
                            predicates.add(cb.equal(root.get("assignedUserId"), currentUserId));
                        } else {
                            predicates.add(cb.disjunction());
                        }
                    }
                    case "OVERDUE" -> predicates.add(
                        cb.and(
                            cb.isNotNull(root.get("targetDeadline")),
                            cb.lessThan(root.get("targetDeadline"), overdueBefore),
                            cb.not(
                                root
                                    .get("status")
                                    .in(PatientComplaintStatus.RESOLVED, PatientComplaintStatus.CLOSED)
                            )
                        )
                    );
                    case "RESOLVED" -> predicates.add(cb.equal(root.get("status"), PatientComplaintStatus.RESOLVED));
                    default -> {}
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
