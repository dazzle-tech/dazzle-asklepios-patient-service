package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingReservation;
import com.dazzle.asklepios.domain.enumeration.billing.BillingReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
    findAllByChargeLine_IdAndStatusOrderByIdAsc(
            Long chargeLineId,
            BillingReservationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingReservation>
    findAllByEncounter_IdAndStatusOrderByIdAsc(
            Long encounterId,
            BillingReservationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingReservation>
    findAllByChargeLine_IdAndStatusOrderByReservedDateDescIdDesc(
            Long chargeLineId,
            BillingReservationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingReservation>
    findAllByChargeResponsibility_IdAndStatusOrderByIdAsc(
            Long chargeResponsibilityId,
            BillingReservationStatus status
    );

    List<BillingReservation>
    findAllByPayment_IdOrderByIdAsc(
            Long paymentId
    );

    /*
     * Read query used by the billing payment GET endpoint.
     * No pessimistic lock is required for this view operation.
     */
    @Query("""
            SELECT r FROM BillingReservation r
            JOIN FETCH r.chargeLine
            JOIN FETCH r.patientServiceProduct
            WHERE r.payment.id = :paymentId
            ORDER BY r.id ASC
            """)
    List<BillingReservation>
    findAllByPayment_IdForReadOrderByIdAsc(
            @Param("paymentId") Long paymentId
    );

    List<BillingReservation>
    findAllByWallet_IdAndStatusOrderByIdAsc(
            Long walletId,
            BillingReservationStatus status
    );

    Optional<BillingReservation>
    findByIdempotencyKey(
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingReservation>
    findAllByChargeResponsibility_IdAndStatusOrderByReservedDateAscIdAsc(
            Long chargeResponsibilityId,
            BillingReservationStatus status
    );
}