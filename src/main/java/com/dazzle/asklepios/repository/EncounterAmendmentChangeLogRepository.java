package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterAmendmentChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface EncounterAmendmentChangeLogRepository extends JpaRepository<EncounterAmendmentChangeLog, Long> {

    List<EncounterAmendmentChangeLog> findByReopenSessionIdInOrderByChangedAtAscIdAsc(Collection<Long> reopenSessionIds);
}
