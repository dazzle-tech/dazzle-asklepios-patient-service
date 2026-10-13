package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ConsultationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ConsultationLogRepository extends JpaRepository<ConsultationLog, Long> {

    List<ConsultationLog> findByReopenSessionIdInOrderByCreatedDateAscIdAsc(Collection<Long> reopenSessionIds);
}
