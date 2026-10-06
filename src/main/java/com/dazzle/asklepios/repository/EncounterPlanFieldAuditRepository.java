package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterPlanFieldAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface EncounterPlanFieldAuditRepository
        extends JpaRepository<EncounterPlanFieldAudit, Long> {

    List<EncounterPlanFieldAudit> findByEncounterPlanIdOrderByLogDateDesc(
            Long encounterPlanId
    );

    List<EncounterPlanFieldAudit> findByReopenSessionIdInOrderByLogDateAscIdAsc(
            Collection<Long> reopenSessionIds
    );
}