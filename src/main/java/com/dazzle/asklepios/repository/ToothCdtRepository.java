package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ToothCdt;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import com.dazzle.asklepios.repository.projection.DentalChartProjections;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ToothCdtRepository extends JpaRepository<ToothCdt, Long> {

    List<ToothCdt> findByToothIdAndCancelledFalse(Long toothId);

    List<ToothCdt> findByToothActionIdAndCancelledFalse(Long toothActionId);

    List<DentalChartProjections.ToothIdView> findDistinctByToothIdInAndCancelledFalse(Collection<Long> toothIds);

    Optional<ToothCdt> findByToothIdAndCdtCodeIdAndSurfaceAndCancelledFalse(Long toothId, Long cdtCodeId, ToothSurface surface);
}
