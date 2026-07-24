package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AvailabilityGenerationBatch;
import com.dazzle.asklepios.domain.enumeration.BatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AvailabilityGenerationBatchRepository extends JpaRepository<AvailabilityGenerationBatch, Long> {
    Page<AvailabilityGenerationBatch> findAllByTemplate_IdInOrderByApplyStartDateTimeDesc(List<Long> templateIds, Pageable pageable);

    Page<AvailabilityGenerationBatch> findAllByTemplate_IdAndIdNotOrderByApplyStartDateTimeDesc(Long templateId, Long batchId, Pageable pageable);

    List<AvailabilityGenerationBatch>
    findTop100ByApplyEndDateTimeLessThanEqualAndIntervalEndedNotificationSentFalseAndExecutionStatusOrderByApplyEndDateTimeAsc(
            Instant now,
            BatchStatus executionStatus
    );
}
