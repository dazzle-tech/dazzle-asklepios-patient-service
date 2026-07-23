package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.enumeration.Currency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillingWalletRepository
        extends JpaRepository<BillingWallet, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingWallet> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingWallet> findByPatient_Id(
            Long patientId
    );

    Optional<BillingWallet>
    findFirstByPatient_IdOrderByIdAsc(
            Long patientId
    );

    boolean existsByPatient_IdAndCurrency(
            Long patientId,
            Currency currency
    );
}