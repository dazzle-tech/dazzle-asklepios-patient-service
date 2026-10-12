package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.VitalSignsLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface VitalSignsLogRepository extends JpaRepository<VitalSignsLog, Long> {

    List<VitalSignsLog> findByReopenSessionIdInOrderByCreatedDateAscIdAsc(Collection<Long> reopenSessionIds);
}
