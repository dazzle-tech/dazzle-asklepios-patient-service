package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingPaymentRepository
        extends JpaRepository<BillingPayment, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingPayment> findById(
            Long id
    );

    Optional<BillingPayment> findByIdempotencyKey(
            String idempotencyKey
    );

    Optional<BillingPayment> findByPaymentNumber(
            String paymentNumber
    );

    List<BillingPayment>
    findAllByWallet_IdAndStatusInOrderByPaymentDateAscIdAsc(
            Long walletId,
            Collection<BillingPaymentStatus> statuses
    );

    List<BillingPayment>
    findAllByPatient_IdAndCurrencyAndStatusInOrderByPaymentDateAscIdAsc(
            Long patientId,
            Currency currency,
            Collection<BillingPaymentStatus> statuses
    );
}