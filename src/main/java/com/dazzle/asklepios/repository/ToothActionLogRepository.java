package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ToothActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToothActionLogRepository extends JpaRepository<ToothActionLog, Long> {

    List<ToothActionLog> findByToothIdOrderByLogTimeDesc(Long toothId);

    List<ToothActionLog> findByToothIdInOrderByLogTimeDesc(List<Long> toothIds);
}
