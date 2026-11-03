package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterAssignToBed;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.service.dailybriefing.BriefingScope;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class DailyBriefingBedSpecification {

    private DailyBriefingBedSpecification() {
    }

    public static Specification<EncounterAssignToBed> occupiedAt(BriefingScope scope, EncounterType type, Instant point) {
        return (root, query, cb) -> {
            Join<EncounterAssignToBed, PatientEncounter> encounter = root.join("encounter", JoinType.INNER);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(encounter.get("encounterType"), type));
            predicates.add(cb.lessThanOrEqualTo(root.get("assignedAt"), point));
            predicates.add(cb.or(cb.isNull(root.get("releasedAt")), cb.greaterThan(root.get("releasedAt"), point)));
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
        };
    }
}
