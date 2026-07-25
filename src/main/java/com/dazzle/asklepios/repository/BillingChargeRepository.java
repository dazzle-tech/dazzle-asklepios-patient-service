package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingChargeRepository
        extends JpaRepository<BillingCharge, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingCharge> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingCharge>
    findFirstByEncounter_IdAndPatient_IdAndCurrencyAndStatusInOrderByIdAsc(
            Long encounterId,
            Long patientId,
            Currency currency,
            Collection<BillingChargeStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingCharge>
    findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
            Long encounterId,
            Collection<BillingChargeStatus> statuses
    );

    Optional<BillingCharge>
    findFirstByEncounter_IdAndStatusNotInOrderByIdDesc(
            Long encounterId,
            Collection<BillingChargeStatus> excludedStatuses
    );

}