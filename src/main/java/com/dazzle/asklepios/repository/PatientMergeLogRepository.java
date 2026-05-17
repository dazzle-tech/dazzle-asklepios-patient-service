package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientMergeLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PatientMergeLogRepository extends JpaRepository<PatientMergeLog, Long>,
        JpaSpecificationExecutor<PatientMergeLog> {

    @EntityGraph(attributePaths = {
            "fromPatient",
            "toPatient"
    })
    List<PatientMergeLog> findByFromPatientIdOrToPatientIdOrderByMergedAtDesc(
            Long fromPatientId,
            Long toPatientId
    );

    List<PatientMergeLog> findAllByOrderByMergedAtDesc();

    @EntityGraph(attributePaths = {
            "toPatient"
    })
    boolean existsByToPatientIdAndMergedAtAfterAndMergeStatus(
            Long toPatientId,
            Instant mergedAt,
            String mergeStatus
    );
    boolean existsByTransactionNumber(String transactionNumber);
}