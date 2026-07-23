package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingChargeLineRepository
        extends JpaRepository<BillingChargeLine, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingChargeLine> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingChargeLine>
    findFirstByPatientServiceProduct_IdAndStatusNotInOrderByIdAsc(
            Long patientServiceProductId,
            Collection<BillingChargeLineStatus> excludedStatuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingChargeLine>
    findAllByCharge_IdAndStatusNotInOrderByIdAsc(
            Long chargeId,
            Collection<BillingChargeLineStatus> excludedStatuses
    );
}