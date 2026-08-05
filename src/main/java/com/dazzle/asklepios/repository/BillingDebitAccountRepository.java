package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingDebitAccount;
import com.dazzle.asklepios.domain.enumeration.Currency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface BillingDebitAccountRepository
        extends JpaRepository<BillingDebitAccount, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingDebitAccount> findById(
            Long id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingDebitAccount>
    findByPatient_IdAndCurrency(
            Long patientId,
            Currency currency
    );

    Optional<BillingDebitAccount>
    findFirstByPatient_IdAndCurrencyOrderByIdAsc(
            Long patientId,
            Currency currency
    );

    Optional<BillingDebitAccount>
    findByAccountNumber(
            String accountNumber
    );

    boolean existsByPatient_IdAndCurrency(
            Long patientId,
            Currency currency
    );

    @Query("""
        select coalesce(sum(a.currentDebitBalance), 0)
        from BillingDebitAccount a
        where a.patient.id = :patientId
          and a.status = com.dazzle.asklepios.domain.enumeration.billing.BillingDebitAccountStatus.ACTIVE
    """)
    BigDecimal sumActiveDebitBalanceByPatient(
            @Param("patientId") Long patientId
    );
}