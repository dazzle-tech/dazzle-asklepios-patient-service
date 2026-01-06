// src/main/java/com/dazzle/asklepios/repository/DiagnosticOrderTestRepository.java
package com.dazzle.asklepios.repository;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DiagnosticOrderTestRepository extends JpaRepository<DiagnosticOrderTest, Long>,
        JpaSpecificationExecutor<DiagnosticOrderTest> {

    Page<DiagnosticOrderTest> findByOrderId(Long orderId, Pageable pageable);

    Page<DiagnosticOrderTest> findByOrderIdAndStatus(Long orderId, DiagnosticOrderTestStatus status, Pageable pageable);

    Page<DiagnosticOrderTest> findByOrderIdAndStatusNotIn(Long orderId, Collection<DiagnosticOrderTestStatus> statuses, Pageable pageable);

    @Query("""
            select distinct t.processingStatus
            from DiagnosticOrderTest t
            where t.orderId = :orderId
              and t.orderType = :type
              and t.status <> com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus.CANCELLED
            """)
    List<DiagnosticStatus> findDistinctProcessingStatuses(Long orderId, TestType type);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
    update DiagnosticOrderTest t
       set t.status = :newStatus
     where t.orderId = :orderId
       and t.status <> com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus.CANCELLED
""")
    int bulkUpdateStatusForOrder(Long orderId, DiagnosticOrderTestStatus newStatus);
}
