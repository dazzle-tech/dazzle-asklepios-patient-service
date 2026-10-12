package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AvailabilityGenerationBatch;
import com.dazzle.asklepios.domain.enumeration.BatchStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AvailabilityGenerationBatchRepository extends JpaRepository<AvailabilityGenerationBatch, Long> {
    Page<AvailabilityGenerationBatch> findAllByTemplate_IdInOrderByApplyStartDateTimeDesc(List<Long> templateIds, Pageable pageable);

    Page<AvailabilityGenerationBatch>
    findAllByTemplate_IdAndIdNotAndApplyStartDateTimeGreaterThanEqualOrderByApplyStartDateTimeAsc(Long templateId, Long batchId, Instant now, Pageable pageable);

    List<AvailabilityGenerationBatch>
    findTop100ByApplyEndDateTimeLessThanEqualAndIntervalEndedNotificationSentFalseAndExecutionStatusOrderByApplyEndDateTimeAsc(
            Instant now,
            BatchStatus executionStatus
    );

    @Query("""
            select b
            from AvailabilityGenerationBatch b
            join b.template t
            where b.executionStatus = :executionStatus
              and b.applyEndDateTime > :now
              and b.applyEndDateTime <= :weekAhead
              and (
                    b.intervalEndingSoonNotificationSentAt is null
                    or b.intervalEndingSoonNotificationSentAt < :dayStart
              )
              and not exists (
                    select 1
                    from AvailabilityGenerationBatch later
                    join later.template lt
                    where later.id <> b.id
                      and later.executionStatus = :executionStatus
                      and lt.departmentId = t.departmentId
                      and lt.resourceId = t.resourceId
                      and lt.templateType = t.templateType
                      and (
                            later.applyEndDateTime > b.applyEndDateTime
                            or (later.applyEndDateTime = b.applyEndDateTime and later.id > b.id)
                      )
              )
            order by b.applyEndDateTime asc
            """)
    List<AvailabilityGenerationBatch> findEndingSoonForDailyNotification(
            @Param("now") Instant now,
            @Param("weekAhead") Instant weekAhead,
            @Param("dayStart") Instant dayStart,
            @Param("executionStatus") BatchStatus executionStatus,
            Pageable pageable
    );

    @Query("""
            select count(b)
            from AvailabilityGenerationBatch b
            join b.template t
            where b.id <> :batchId
              and b.executionStatus = :executionStatus
              and t.departmentId = :departmentId
              and t.resourceId = :resourceId
              and t.templateType = :templateType
              and (
                    b.applyEndDateTime > :applyEndDateTime
                    or (b.applyEndDateTime = :applyEndDateTime and b.id > :batchId)
              )
            """)
    long countLaterCompletedApplyForResource(
            @Param("batchId") Long batchId,
            @Param("departmentId") Long departmentId,
            @Param("resourceId") Long resourceId,
            @Param("templateType") TemplateType templateType,
            @Param("applyEndDateTime") Instant applyEndDateTime,
            @Param("executionStatus") BatchStatus executionStatus
    );
}
