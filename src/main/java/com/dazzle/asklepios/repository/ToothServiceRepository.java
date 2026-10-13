package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ToothService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ToothServiceRepository extends JpaRepository<ToothService, Long> {

    List<ToothService> findByToothIdAndCancelledFalse(Long toothId);

    List<ToothService> findByToothActionIdAndCancelledFalse(Long toothActionId);

    Optional<ToothService> findByToothIdAndServiceIdAndCancelledFalse(Long toothId, Long serviceId);
}
