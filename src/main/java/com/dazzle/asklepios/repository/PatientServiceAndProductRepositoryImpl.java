package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PatientServiceAndProductRepositoryImpl
        implements PatientServiceAndProductRepositoryCustom {

    private final EntityManager entityManager;

    public PatientServiceAndProductRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public long countDistinctEncounters(
            Specification<PatientServiceAndProduct> specification
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<PatientServiceAndProduct> root = query.from(PatientServiceAndProduct.class);
        query.select(cb.countDistinct(root.get("encounterId")));
        applySpecification(specification, root, query, cb);
        Long total = entityManager.createQuery(query).getSingleResult();
        return total == null ? 0L : total;
    }

    @Override
    public List<Long> findDistinctEncounterIds(
            Specification<PatientServiceAndProduct> specification,
            Pageable pageable
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<PatientServiceAndProduct> root = query.from(PatientServiceAndProduct.class);
        Path<Long> encounterId = root.get("encounterId");
        Expression<Instant> newestCreatedDate =
                cb.greatest(root.<Instant>get("createdDate"));
        Expression<Long> newestItemId = cb.max(root.<Long>get("id"));

        query.multiselect(encounterId, newestCreatedDate, newestItemId);
        applySpecification(specification, root, query, cb);
        query.groupBy(encounterId);
        query.orderBy(
                cb.desc(newestCreatedDate),
                cb.desc(newestItemId),
                cb.desc(encounterId)
        );

        long offset = pageable.getOffset();
        List<Tuple> page = entityManager.createQuery(query)
                .setFirstResult(offset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offset)
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        List<Long> encounterIds = new ArrayList<>(page.size());
        for (Tuple tuple : page) {
            Object value = tuple.get(0);
            if (value instanceof Number number) {
                encounterIds.add(number.longValue());
            }
        }
        return encounterIds;
    }

    private static void applySpecification(
            Specification<PatientServiceAndProduct> specification,
            Root<PatientServiceAndProduct> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb
    ) {
        Predicate predicate = specification == null
                ? null
                : specification.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
    }
}
