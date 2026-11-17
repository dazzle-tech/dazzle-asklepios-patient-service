package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ToothService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ToothServiceRepository extends JpaRepository<ToothService, Long> {

    List<ToothService> findByToothIdAndCancelledFalse(Long toothId);

    List<ToothService> findByToothActionIdAndCancelledFalse(Long toothActionId);

    Optional<ToothService> findByToothIdAndServiceIdAndCancelledFalse(Long toothId, Long serviceId);

    @Query("""
            select distinct s.toothId
            from ToothService s
            where s.toothId in :toothIds
              and s.cancelled = false
            """)
    List<Long> findToothIdsWithActiveServices(@Param("toothIds") Collection<Long> toothIds);
}
