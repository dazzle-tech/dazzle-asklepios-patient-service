package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ToothService;
import com.dazzle.asklepios.repository.projection.DentalChartProjections;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ToothServiceRepository extends JpaRepository<ToothService, Long> {

    List<ToothService> findByToothIdAndCancelledFalse(Long toothId);

    List<ToothService> findByToothActionIdAndCancelledFalse(Long toothActionId);

    List<DentalChartProjections.ToothIdView> findDistinctByToothIdInAndCancelledFalse(Collection<Long> toothIds);
}
