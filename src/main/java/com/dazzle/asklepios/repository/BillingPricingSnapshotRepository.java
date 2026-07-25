package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingPricingSnapshot;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPricingSnapshotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingPricingSnapshotRepository
        extends JpaRepository<BillingPricingSnapshot, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingPricingSnapshot> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingPricingSnapshot>
    findFirstByChargeLine_IdAndStatusOrderByIdDesc(
            Long chargeLineId,
            BillingPricingSnapshotStatus status
    );

    /*
     * Read query used by the encounter billing summary endpoint.
     * No pessimistic lock is required for this view operation.
     */
    Optional<BillingPricingSnapshot>
    findTopByChargeLine_IdAndStatusOrderByIdDesc(
            Long chargeLineId,
            BillingPricingSnapshotStatus status
    );

    List<BillingPricingSnapshot>
    findAllByChargeLine_IdOrderByIdDesc(
            Long chargeLineId
    );

    Optional<BillingPricingSnapshot>
    findByIdempotencyKey(
            String idempotencyKey
    );

    boolean existsByIdempotencyKey(
            String idempotencyKey
    );
}