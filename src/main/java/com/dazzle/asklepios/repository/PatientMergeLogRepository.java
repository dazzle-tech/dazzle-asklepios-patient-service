package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientMergeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PatientMergeLogRepository extends JpaRepository<PatientMergeLog, Long>,
        JpaSpecificationExecutor<PatientMergeLog> {

    Optional<PatientMergeLog> findByFromPatientIdAndToPatientIdAndMergeStatus(
            Long fromPatientId,
            Long toPatientId,
            String mergeStatus
    );

    List<PatientMergeLog> findByFromPatientIdOrToPatientIdOrderByMergedAtDesc(
            Long fromPatientId,
            Long toPatientId
    );

    List<PatientMergeLog> findAllByOrderByMergedAtDesc();

    boolean existsByToPatientIdAndMergedAtAfterAndMergeStatus(
            Long toPatientId,
            Instant mergedAt,
            String mergeStatus
    );
}