package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PointOfSaleWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PointOfSaleWebhookLogRepository
        extends JpaRepository<
        PointOfSaleWebhookLog,
        Long
        >,
        JpaSpecificationExecutor<
                        PointOfSaleWebhookLog
                        > {
}
