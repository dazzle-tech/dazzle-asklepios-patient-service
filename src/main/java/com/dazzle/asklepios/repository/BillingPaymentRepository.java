package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingPayment;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPaymentStatus;
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
public interface BillingPaymentRepository
        extends JpaRepository<BillingPayment, Long> {

    @Override
    Optional<BillingPayment> findById(
            Long id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BillingPayment p where p.id = :id")
    Optional<BillingPayment> findByIdForUpdate(
            @Param("id") Long id
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

    List<BillingPayment>
    findAllByPatient_IdAndStatusInOrderByPaymentDateDescIdDesc(
            Long patientId,
            Collection<BillingPaymentStatus> statuses
    );

    List<BillingPayment> findAllByEncounter_IdOrderByIdAsc(Long encounterId);

    @Query(
            value = """
                    SELECT MAX(
                        CAST(
                            SUBSTRING(bp.receipt_number FROM '([0-9]+)$')
                            AS BIGINT
                        )
                    )
                    FROM billing_payment bp
                    INNER JOIN patient_encounters pe ON pe.id = bp.encounter_id
                    WHERE pe.facility_id = :facilityId
                      AND bp.receipt_number IS NOT NULL
                      AND EXTRACT(YEAR FROM bp.payment_date) = :year
                    """,
            nativeQuery = true
    )
    Optional<Long> findMaxIssuedReceiptSequenceForYear(
            @Param("facilityId") Long facilityId,
            @Param("year") int year
    );
}