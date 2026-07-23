package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingDebitTransaction;
import com.dazzle.asklepios.domain.enumeration.billing.BillingDebitTransactionStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingDebitTransactionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingDebitTransactionRepository
        extends JpaRepository<BillingDebitTransaction, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingDebitTransaction> findById(
            Long id
    );

    Optional<BillingDebitTransaction>
    findByIdempotencyKey(
            String idempotencyKey
    );

    Optional<BillingDebitTransaction>
    findByTransactionNumber(
            String transactionNumber
    );

    List<BillingDebitTransaction>
    findAllByDebitAccount_IdOrderByTransactionDateAscIdAsc(
            Long debitAccountId
    );

    List<BillingDebitTransaction>
    findAllByPatient_IdOrderByTransactionDateDescIdDesc(
            Long patientId
    );

    List<BillingDebitTransaction>
    findAllByChargeResponsibility_IdOrderByTransactionDateAscIdAsc(
            Long chargeResponsibilityId
    );

    List<BillingDebitTransaction>
    findAllByTransactionGroupIdOrderByIdAsc(
            UUID transactionGroupId
    );

    List<BillingDebitTransaction>
    findAllByDebitAccount_IdAndTransactionTypeAndStatusOrderByTransactionDateAscIdAsc(
            Long debitAccountId,
            BillingDebitTransactionType transactionType,
            BillingDebitTransactionStatus status
    );
}