package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

public final class DentalPlannedTreatmentSpecification {

    private DentalPlannedTreatmentSpecification() {
    }

    public static Specification<DentalPlannedTreatment> touchingEncounter(
            Long encounterId,
            Collection<PlannedTreatmentStatus> statuses
    ) {
        return (root, query, cb) -> {
            Join<DentalPlannedTreatment, PatientEncounter> executionEncounter =
                    root.join("executionEncounter", JoinType.LEFT);
            return cb.and(
                    cb.isFalse(root.get("cancelled")),
                    root.get("status").in(statuses),
                    cb.or(
                            cb.equal(root.get("encounterId"), encounterId),
                            cb.equal(executionEncounter.get("id"), encounterId)
                    )
            );
        };
    }
}
