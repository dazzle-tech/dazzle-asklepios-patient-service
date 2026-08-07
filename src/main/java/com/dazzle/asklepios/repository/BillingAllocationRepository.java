package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingAllocation;
import com.dazzle.asklepios.domain.enumeration.billing.BillingAllocationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingAllocationRepository extends JpaRepository<BillingAllocation, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingAllocation> findById( Long id);

    Optional<BillingAllocation>
    findByIdempotencyKey(String idempotencyKey);

    List<BillingAllocation>
    findAllByChargeLine_IdAndStatusInOrderByAllocationDateDescIdDesc(
            Long chargeLineId,
            Collection<BillingAllocationStatus> statuses
    );
    Optional<BillingAllocation>
    findByAllocationNumber(
            String allocationNumber
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingAllocation>
    findAllByChargeLine_IdAndStatusOrderByAllocationDateAscIdAsc(
            Long chargeLineId,
            BillingAllocationStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingAllocation>
    findAllByChargeResponsibility_IdAndStatusOrderByAllocationDateAscIdAsc(
            Long chargeResponsibilityId,
            BillingAllocationStatus status
    );

    List<BillingAllocation>
    findAllByReservation_IdOrderByAllocationDateAscIdAsc(
            Long reservationId
    );

    List<BillingAllocation>
    findAllByPayment_IdOrderByAllocationDateAscIdAsc(
            Long paymentId
    );

    /*
     * Read query used by the billing payment GET endpoint.
     * No pessimistic lock is required for this view operation.
     */
    @Query("""
            SELECT a FROM BillingAllocation a
            JOIN FETCH a.chargeLine
            JOIN FETCH a.patientServiceProduct
            LEFT JOIN FETCH a.reservation
            WHERE a.payment.id = :paymentId
            ORDER BY a.allocationDate ASC, a.id ASC
            """)
    List<BillingAllocation>
    findAllByPayment_IdForReadOrderByAllocationDateAscIdAsc(
            @Param("paymentId") Long paymentId
    );


    List<BillingAllocation>
    findAllByDebitTransactionIdOrderByAllocationDateAscIdAsc(
            Long debitTransactionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingAllocation>
    findFirstByDebitTransactionIdAndStatusInOrderByIdDesc(
            Long debitTransactionId,
            Collection<BillingAllocationStatus> statuses
    );

    List<BillingAllocation>
    findAllByEncounter_IdAndCharge_IdAndStatusInOrderByAllocationDateAscIdAsc(
            Long encounterId,
            Long chargeId,
            Collection<BillingAllocationStatus> statuses
    );

}