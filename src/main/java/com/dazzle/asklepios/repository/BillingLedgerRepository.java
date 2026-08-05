package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingLedger;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerTransactionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingLedgerRepository
        extends JpaRepository<BillingLedger, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingLedger> findById(Long id);

    Optional<BillingLedger> findByIdempotencyKey(
            String idempotencyKey
    );

    Optional<BillingLedger> findByLedgerNumber(
            String ledgerNumber
    );


    List<BillingLedger>
    findAllByTransactionGroupIdOrderByIdAsc(
            UUID transactionGroupId
    );

    List<BillingLedger>
    findAllByPatient_IdOrderByTransactionDateDescIdDesc(
            Long patientId
    );

    List<BillingLedger>
    findAllByEncounter_IdOrderByTransactionDateAscIdAsc(
            Long encounterId
    );

    List<BillingLedger>
    findAllByPayment_IdOrderByTransactionDateAscIdAsc(
            Long paymentId
    );

    List<BillingLedger>
    findAllByReservation_IdOrderByTransactionDateAscIdAsc(
            Long reservationId
    );

    List<BillingLedger>
    findAllByAllocation_IdOrderByTransactionDateAscIdAsc(
            Long allocationId
    );

    Optional<BillingLedger>
    findFirstByAllocation_IdAndTransactionTypeOrderByIdAsc(
            Long allocationId,
            BillingLedgerTransactionType transactionType
    );
}