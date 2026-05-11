package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientMergeItemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PatientMergeItemLogRepository extends JpaRepository<PatientMergeItemLog, Long>,
        JpaSpecificationExecutor<PatientMergeItemLog> {

    List<PatientMergeItemLog> findByMergeLogIdOrderByIdAsc(Long mergeLogId);
}