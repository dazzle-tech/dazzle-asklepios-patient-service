package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingRefund;
import com.dazzle.asklepios.domain.enumeration.billing.BillingRefundStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingRefundRepository
        extends JpaRepository<BillingRefund, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingRefund> findById(
            Long id
    );

    Optional<BillingRefund> findByIdempotencyKey(
            String idempotencyKey
    );

    Optional<BillingRefund> findByRefundNumber(
            String refundNumber
    );

    List<BillingRefund>
    findAllByOriginalPayment_IdOrderByRequestDateAscIdAsc(
            Long originalPaymentId
    );

    List<BillingRefund>
    findAllByOriginalPayment_IdAndStatusInOrderByRequestDateAscIdAsc(
            Long originalPaymentId,
            Collection<BillingRefundStatus> statuses
    );

    List<BillingRefund>
    findAllByWallet_IdOrderByRequestDateDescIdDesc(
            Long walletId
    );

    List<BillingRefund>
    findAllByPatient_IdOrderByRequestDateDescIdDesc(
            Long patientId
    );

    List<BillingRefund>
    findAllByTransactionGroupIdOrderByIdAsc(
            UUID transactionGroupId
    );

    Optional<BillingRefund>
    findFirstByOriginalRefund_IdAndStatusOrderByIdDesc(
            Long originalRefundId,
            BillingRefundStatus status
    );
}