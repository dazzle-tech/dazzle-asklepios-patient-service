package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingReservation;
import com.dazzle.asklepios.domain.enumeration.billing.BillingReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingReservationRepository
        extends JpaRepository<BillingReservation, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingReservation> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingReservation>
    findAllByChargeLine_IdAndStatusInOrderByIdAsc(
            Long chargeLineId,
            Collection<BillingReservationStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingReservation>
    findAllByEncounter_IdAndStatusInOrderByIdAsc(
            Long encounterId,
            Collection<BillingReservationStatus> statuses
    );

    List<BillingReservation>
    findAllByWallet_IdAndStatusInOrderByIdAsc(
            Long walletId,
            Collection<BillingReservationStatus> statuses
    );

    Optional<BillingReservation>
    findByIdempotencyKey(
            String idempotencyKey
    );

    BigDecimalProjection
    findFirstByChargeLine_IdAndStatusInOrderByIdAsc(
            Long chargeLineId,
            Collection<BillingReservationStatus> statuses
    );

    interface BigDecimalProjection {

        java.math.BigDecimal getRemainingReservedAmount();
    }
}