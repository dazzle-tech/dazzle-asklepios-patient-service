package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentStatus;
import com.dazzle.asklepios.repository.projection.OpenTreatmentCount;

import java.util.Collection;
import java.util.List;

public interface DentalPlannedTreatmentRepositoryCustom {

    /**
     * Open (not cancelled, not {@code completedStatus}) planned treatments per tooth id, from one grouped query.
     * Teeth without open treatments are not returned.
     */
    List<OpenTreatmentCount> countOpenByToothIds(
            Collection<Long> toothIds,
            PlannedTreatmentStatus completedStatus
    );
}
