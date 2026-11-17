package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentStatus;
import com.dazzle.asklepios.repository.projection.OpenTreatmentCount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.util.Collection;
import java.util.List;

public class DentalPlannedTreatmentRepositoryImpl implements DentalPlannedTreatmentRepositoryCustom {

    private final EntityManager entityManager;

    public DentalPlannedTreatmentRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<OpenTreatmentCount> countOpenByToothIds(
            Collection<Long> toothIds,
            PlannedTreatmentStatus completedStatus
    ) {
        if (toothIds == null || toothIds.isEmpty()) {
            return List.of();
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<OpenTreatmentCount> query = cb.createQuery(OpenTreatmentCount.class);
        Root<DentalPlannedTreatment> root = query.from(DentalPlannedTreatment.class);
        query.select(cb.construct(
                OpenTreatmentCount.class,
                root.<Long>get("toothId"),
                cb.count(root)
        ));
        query.where(
                root.get("toothId").in(toothIds),
                cb.isFalse(root.get("cancelled")),
                cb.notEqual(root.get("status"), completedStatus)
        );
        query.groupBy(root.get("toothId"));
        return entityManager.createQuery(query).getResultList();
    }
}
