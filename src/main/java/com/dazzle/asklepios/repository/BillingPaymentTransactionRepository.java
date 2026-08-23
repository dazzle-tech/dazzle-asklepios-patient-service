package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingPaymentTransaction;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentTransactionStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentTransactionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingPaymentTransactionRepository
        extends JpaRepository<BillingPaymentTransaction, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingPaymentTransaction> findById(
            Long id
    );

    Optional<BillingPaymentTransaction>
    findByIdempotencyKey(
            String idempotencyKey
    );

    Optional<BillingPaymentTransaction>
    findByTransactionNumber(
            String transactionNumber
    );

    List<BillingPaymentTransaction>
    findAllByPayment_IdOrderByTransactionDateAscIdAsc(
            Long paymentId
    );

    List<BillingPaymentTransaction>
    findAllByPayment_IdInOrderByTransactionDateAscIdAsc(
            List<Long> paymentIds
    );

    List<BillingPaymentTransaction>
    findAllByPayment_IdAndTransactionTypeAndStatusOrderByTransactionDateAscIdAsc(
            Long paymentId,
            BillingPaymentTransactionType transactionType,
            BillingPaymentTransactionStatus status
    );
}