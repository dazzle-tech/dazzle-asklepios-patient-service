package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingChargeRepository
        extends JpaRepository<BillingCharge, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingCharge> findById(Long id);

    @Query("SELECT bc FROM BillingCharge bc WHERE bc.id = :id")
    Optional<BillingCharge> findByIdWithoutLock(
            @Param("id") Long id
    );

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

    Optional<BillingCharge>
    findFirstByEncounter_IdOrderByIdDesc(
            Long encounterId
    );

    List<BillingCharge>
    findAllByPatient_IdAndStatusNotInOrderByIdAsc(
            Long patientId,
            Collection<BillingChargeStatus> excludedStatuses
    );

    @Query("""
        select coalesce(sum(c.outstandingAmount), 0)
        from BillingCharge c
        where c.patient.id = :patientId
          and c.status not in :closedStatuses
          and c.outstandingAmount > 0
    """)
    BigDecimal sumOpenOutstandingByPatient(
            @Param("patientId") Long patientId,
            @Param("closedStatuses") Collection<BillingChargeStatus> closedStatuses
    );

    default BigDecimal sumOpenOutstandingByPatient(Long patientId) {
        return sumOpenOutstandingByPatient(
                patientId,
                List.of(
                        BillingChargeStatus.CLOSED,
                        BillingChargeStatus.CANCELLED,
                        BillingChargeStatus.REVERSED
                )
        );
    }

    @Query("""
        select coalesce(sum(c.outstandingAmount), 0)
        from BillingCharge c
        where c.patient.id = :patientId
          and c.status not in :closedStatuses
          and c.outstandingAmount > 0
          and c.encounter.id not in :excludedEncounterIds
    """)
    BigDecimal sumOpenOutstandingByPatientExcludingEncounters(
            @Param("patientId") Long patientId,
            @Param("closedStatuses") Collection<BillingChargeStatus> closedStatuses,
            @Param("excludedEncounterIds") Collection<Long> excludedEncounterIds
    );

    default BigDecimal sumOpenOutstandingByPatientExcludingEncounters(
            Long patientId,
            Collection<Long> excludedEncounterIds
    ) {
        if (excludedEncounterIds == null || excludedEncounterIds.isEmpty()) {
            return sumOpenOutstandingByPatient(patientId);
        }

        return sumOpenOutstandingByPatientExcludingEncounters(
                patientId,
                List.of(
                        BillingChargeStatus.CLOSED,
                        BillingChargeStatus.CANCELLED,
                        BillingChargeStatus.REVERSED
                ),
                excludedEncounterIds
        );
    }

}