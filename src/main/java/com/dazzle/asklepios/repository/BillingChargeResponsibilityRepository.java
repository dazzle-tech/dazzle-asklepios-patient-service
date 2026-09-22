package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
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

    @Query("""
            select r from BillingChargeResponsibility r
            join fetch r.encounter
            where r.patient.id = :patientId
              and r.status not in :excludedStatuses
            order by r.id asc
            """)
    List<BillingChargeResponsibility>
    findAllByPatient_IdAndStatusNotInOrderByIdAsc(
            @Param("patientId") Long patientId,
            @Param("excludedStatuses") Collection<BillingResponsibilityStatus> excludedStatuses
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

    @Query("""
            SELECT r.payerId, r.currency,
                   COALESCE(SUM(r.responsibilityAmount), 0),
                   COALESCE(SUM(r.allocatedAmount), 0),
                   COALESCE(SUM(r.outstandingAmount), 0)
            FROM BillingChargeResponsibility r
            WHERE r.responsiblePartyType = :partyType
              AND r.status NOT IN :excludedStatuses
              AND r.payerId IS NOT NULL
              AND (:facilityId IS NULL OR r.encounter.facilityId = :facilityId)
            GROUP BY r.payerId, r.currency
            """)
    List<Object[]> aggregateInsuranceTotalsByPayer(
            @Param("partyType") ResponsiblePartyType partyType,
            @Param("excludedStatuses")
            Collection<BillingResponsibilityStatus> excludedStatuses,
            @Param("facilityId") Long facilityId
    );

    @Query("""
            SELECT r.payerId, COUNT(DISTINCT r.claimId)
            FROM BillingChargeResponsibility r
            WHERE r.responsiblePartyType = :partyType
              AND r.claimId IS NOT NULL
              AND r.allocatedAmount > 0
              AND r.outstandingAmount > 0
              AND r.status NOT IN :excludedStatuses
              AND (:facilityId IS NULL OR r.encounter.facilityId = :facilityId)
            GROUP BY r.payerId
            """)
    List<Object[]> countPartiallyPaidClaimsByPayer(
            @Param("partyType") ResponsiblePartyType partyType,
            @Param("excludedStatuses")
            Collection<BillingResponsibilityStatus> excludedStatuses,
            @Param("facilityId") Long facilityId
    );

    @Query("""
            select coalesce(sum(r.outstandingAmount), 0)
            from BillingChargeResponsibility r
            join r.charge c
            where r.patient.id = :patientId
              and r.responsiblePartyType = :partyType
              and r.status not in :excludedStatuses
              and r.outstandingAmount > 0
              and c.status not in :closedChargeStatuses
            """)
    BigDecimal sumOpenOutstandingByPatientAndPartyType(
            @Param("patientId") Long patientId,
            @Param("partyType") ResponsiblePartyType partyType,
            @Param("excludedStatuses")
            Collection<BillingResponsibilityStatus> excludedStatuses,
            @Param("closedChargeStatuses")
            Collection<com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus> closedChargeStatuses
    );

    default BigDecimal sumOpenPatientOutstandingByPatient(Long patientId) {
        return sumOpenOutstandingByPatientAndPartyType(
                patientId,
                ResponsiblePartyType.PATIENT,
                List.of(
                        BillingResponsibilityStatus.CANCELLED,
                        BillingResponsibilityStatus.SUPERSEDED
                ),
                List.of(
                        com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus.CLOSED,
                        com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus.CANCELLED,
                        com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus.REVERSED
                )
        );
    }

    @Query("""
            select coalesce(sum(r.outstandingAmount), 0)
            from BillingChargeResponsibility r
            join r.charge c
            where r.patient.id = :patientId
              and r.responsiblePartyType = :partyType
              and r.status not in :excludedStatuses
              and r.outstandingAmount > 0
              and c.status not in :closedChargeStatuses
              and c.encounter.id not in :excludedEncounterIds
            """)
    BigDecimal sumOpenOutstandingByPatientAndPartyTypeExcludingEncounters(
            @Param("patientId") Long patientId,
            @Param("partyType") ResponsiblePartyType partyType,
            @Param("excludedStatuses")
            Collection<BillingResponsibilityStatus> excludedStatuses,
            @Param("closedChargeStatuses")
            Collection<com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus> closedChargeStatuses,
            @Param("excludedEncounterIds") Collection<Long> excludedEncounterIds
    );

    default BigDecimal sumOpenPatientOutstandingByPatientExcludingEncounters(
            Long patientId,
            Collection<Long> excludedEncounterIds
    ) {
        if (excludedEncounterIds == null || excludedEncounterIds.isEmpty()) {
            return sumOpenPatientOutstandingByPatient(patientId);
        }

        return sumOpenOutstandingByPatientAndPartyTypeExcludingEncounters(
                patientId,
                ResponsiblePartyType.PATIENT,
                List.of(
                        BillingResponsibilityStatus.CANCELLED,
                        BillingResponsibilityStatus.SUPERSEDED
                ),
                List.of(
                        com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus.CLOSED,
                        com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus.CANCELLED,
                        com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus.REVERSED
                ),
                excludedEncounterIds
        );
    }

    default BigDecimal sumOpenInsuranceOutstandingByPatient(Long patientId) {
        return sumOpenOutstandingByPatientAndPartyType(
                patientId,
                ResponsiblePartyType.INSURANCE,
                List.of(
                        BillingResponsibilityStatus.CANCELLED,
                        BillingResponsibilityStatus.SUPERSEDED
                ),
                List.of(
                        com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus.CLOSED,
                        com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus.CANCELLED,
                        com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus.REVERSED
                )
        );
    }
}