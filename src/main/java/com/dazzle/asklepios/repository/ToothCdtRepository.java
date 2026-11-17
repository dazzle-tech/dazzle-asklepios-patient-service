package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ToothCdt;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ToothCdtRepository extends JpaRepository<ToothCdt, Long> {

    List<ToothCdt> findByToothIdAndCancelledFalse(Long toothId);

    List<ToothCdt> findByToothActionIdAndCancelledFalse(Long toothActionId);

    @Query("""
            select distinct c.toothId
            from ToothCdt c
            where c.toothId in :toothIds
              and c.cancelled = false
            """)
    List<Long> findToothIdsWithActiveCdts(@Param("toothIds") Collection<Long> toothIds);

    Optional<ToothCdt> findByToothIdAndCdtCodeIdAndSurfaceAndCancelledFalse(Long toothId, Long cdtCodeId, ToothSurface surface);
}
