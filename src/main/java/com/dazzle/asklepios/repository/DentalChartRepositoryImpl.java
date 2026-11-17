package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalChart;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Root;
import org.springframework.transaction.annotation.Transactional;

public class DentalChartRepositoryImpl implements DentalChartRepositoryCustom {

    private final EntityManager entityManager;

    public DentalChartRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public int bumpVersionIfCurrent(Long id, Long expectedVersion) {
        entityManager.flush();

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<DentalChart> update = cb.createCriteriaUpdate(DentalChart.class);
        Root<DentalChart> root = update.from(DentalChart.class);
        update.set(root.<Long>get("version"), cb.sum(root.<Long>get("version"), 1L));
        update.where(
                cb.equal(root.get("id"), id),
                cb.equal(root.get("version"), expectedVersion)
        );
        int updated = entityManager.createQuery(update).executeUpdate();

        entityManager.clear();
        return updated;
    }
}
