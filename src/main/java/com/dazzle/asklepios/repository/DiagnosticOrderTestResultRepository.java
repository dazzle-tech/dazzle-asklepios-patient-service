package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface DiagnosticOrderTestResultRepository
        extends JpaRepository<DiagnosticOrderTestResult, Long>,
        JpaSpecificationExecutor<DiagnosticOrderTestResult> {

    // =========================================================
    // PROCESSING STATUS HELPERS
    // =========================================================

    @Query("""
        select r.processingStatus
          from DiagnosticOrderTestResult r
         where r.orderTestId = ?1
           and (r.processingStatus is null
                or r.processingStatus <> 
                   com.dazzle.asklepios.domain.enumeration.DiagnosticStatus.CANCELLED)
    """)
    List<DiagnosticStatus> findProcessingStatusesByOrderTestId(Long orderTestId);


    // =========================================================
    // DISTINCT PROFILE TEST IDS (SINGLE ORDER TEST)
    // =========================================================

    @Query("""
        select distinct r.profileTestId
          from DiagnosticOrderTestResult r
         where r.orderTestId = ?1
    """)
    List<Long> findDistinctProfileTestIdsByOrderTestId(Long orderTestId);


    // =========================================================
    // BULK FETCH (REQUIRED BY SERVICE)
    // =========================================================

    List<DiagnosticOrderTestResult> findByOrderTestIdIn(List<Long> orderTestIds);

    @Query("""
        select r
        from DiagnosticOrderTestResult r
        where r.orderTestId in :orderTestIds
          and r.createdDate between :from and :to
    """)
    List<DiagnosticOrderTestResult> findByOrderTestIdInAndCreatedDateBetween(
            List<Long> orderTestIds, Instant from, Instant to
    );

    @Query("""
    select r
    from DiagnosticOrderTestResult r
    where r.orderTestId in :orderTestIds
      and r.createdDate between :from and :to
      and (:profileTestId is null or r.profileTestId = :profileTestId)
""")
    List<DiagnosticOrderTestResult> findByOrderTestIdsAndOptionalProfileTestId(
            List<Long> orderTestIds,
            Instant from,
            Instant to,
            Long profileTestId
    );
}
