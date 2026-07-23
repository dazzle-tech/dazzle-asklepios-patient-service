package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingChargeResponsibilityRepository
        extends JpaRepository<BillingChargeResponsibility, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingChargeResponsibility> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingChargeResponsibility>
    findAllByChargeLine_IdAndStatusNotInOrderByIdAsc(
            Long chargeLineId,
            Collection<BillingResponsibilityStatus> excludedStatuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingChargeResponsibility>
    findFirstByChargeLine_IdAndResponsiblePartyTypeAndStatusNotInOrderByIdAsc(
            Long chargeLineId,
            ResponsiblePartyType responsiblePartyType,
            Collection<BillingResponsibilityStatus> excludedStatuses
    );

    List<BillingChargeResponsibility>
    findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
            Long encounterId,
            Collection<BillingResponsibilityStatus> excludedStatuses
    );

    Optional<BillingChargeResponsibility>
    findByIdempotencyKey(String idempotencyKey);

    Optional<BillingChargeResponsibility>
    findFirstByChargeLine_IdAndResponsiblePartyTypeAndStatusOrderByIdDesc(
            Long chargeLineId,
            ResponsiblePartyType responsiblePartyType,
            BillingResponsibilityStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingChargeResponsibility>
    findAllByChargeLine_IdOrderByIdAsc(
            Long chargeLineId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingChargeResponsibility>
    findAllByCharge_IdAndResponsiblePartyTypeAndStatusNotInOrderByIdAsc(
            Long chargeId,
            ResponsiblePartyType responsiblePartyType,
            Collection<BillingResponsibilityStatus> excludedStatuses
    );

    List<BillingChargeResponsibility>
    findAllByCharge_IdAndStatusNotInOrderByIdAsc(
            Long chargeId,
            Collection<BillingResponsibilityStatus> excludedStatuses
    );
}