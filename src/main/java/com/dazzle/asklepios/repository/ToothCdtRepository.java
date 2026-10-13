package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ToothCdt;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ToothCdtRepository extends JpaRepository<ToothCdt, Long> {

    List<ToothCdt> findByToothIdAndCancelledFalse(Long toothId);

    List<ToothCdt> findByToothActionIdAndCancelledFalse(Long toothActionId);

    Optional<ToothCdt> findByToothIdAndCdtCodeIdAndSurfaceAndCancelledFalse(Long toothId, Long cdtCodeId, ToothSurface surface);
}
