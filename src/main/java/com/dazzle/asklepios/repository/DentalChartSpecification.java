package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Collection;

public final class DentalChartSpecification {

    private DentalChartSpecification() {
    }

    public static Specification<DentalChart> previousChartsOf(
            Long patientId,
            Long encounterId,
            LocalDate asOfDate,
            Collection<TreatmentStatus> statuses
    ) {
        return (root, query, cb) -> {
            Join<DentalChart, PatientEncounter> encounter = root.join("encounter");
            return cb.and(
                    cb.equal(root.get("patient").get("id"), patientId),
                    cb.notEqual(encounter.get("id"), encounterId),
                    encounter.get("status").in(statuses),
                    cb.isNotNull(encounter.get("encounterDate")),
                    cb.or(
                            cb.lessThan(encounter.get("encounterDate"), asOfDate),
                            cb.and(
                                    cb.equal(encounter.get("encounterDate"), asOfDate),
                                    cb.lessThan(encounter.get("id"), encounterId)
                            )
                    )
            );
        };
    }
}
