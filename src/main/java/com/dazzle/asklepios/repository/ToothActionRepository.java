package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ToothActionRepository extends JpaRepository<ToothAction, Long> {

    List<ToothAction> findByToothId(Long toothId);

    List<ToothAction> findByToothIdAndCancelledFalse(Long toothId);

    List<ToothAction> findByToothIdIn(List<Long> toothIds);

    List<ToothAction> findByToothIdInAndCancelledFalse(List<Long> toothIds);

    Optional<ToothAction> findByToothIdAndDentalActionIdAndSurfaceAndCancelledFalse(Long toothId, Long dentalActionId, ToothSurface surface);
}
