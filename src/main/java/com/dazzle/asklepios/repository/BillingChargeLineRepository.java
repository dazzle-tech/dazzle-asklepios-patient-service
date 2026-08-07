package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
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
public interface BillingChargeLineRepository
        extends JpaRepository<BillingChargeLine, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingChargeLine> findById(Long id);

    @Query("SELECT cl FROM BillingChargeLine cl WHERE cl.id = :id")
    Optional<BillingChargeLine> findByIdWithoutLock(
            @Param("id") Long id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingChargeLine>
    findFirstByPatientServiceProduct_IdAndStatusNotInOrderByIdAsc(
            Long patientServiceProductId,
            Collection<BillingChargeLineStatus> excludedStatuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingChargeLine>
    findFirstByEncounter_IdAndPatientServiceProduct_IdAndStatusNotInOrderByIdAsc(
            Long encounterId,
            Long patientServiceProductId,
            Collection<BillingChargeLineStatus> excludedStatuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BillingChargeLine>
    findAllByCharge_IdAndStatusNotInOrderByIdAsc(
            Long chargeId,
            Collection<BillingChargeLineStatus> excludedStatuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BillingChargeLine>
    findFirstByPatientServiceProduct_IdOrderByIdDesc(
            Long patientServiceProductId
    );

    List<BillingChargeLine>
    findAllByCharge_IdAndStatusNotOrderByIdAsc(
            Long chargeId,
            BillingChargeLineStatus excludedStatus
    );

    /*
     * Read query used by the encounter billing summary endpoint.
     * No pessimistic lock is required for this view operation.
     */
    @Query("""
            SELECT cl FROM BillingChargeLine cl
            JOIN FETCH cl.charge
            JOIN FETCH cl.patientServiceProduct
            WHERE cl.encounter.id = :encounterId
              AND cl.status NOT IN :excludedStatuses
            ORDER BY cl.id ASC
            """)
    List<BillingChargeLine>
    findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
            @Param("encounterId") Long encounterId,
            @Param("excludedStatuses")
            Collection<BillingChargeLineStatus> excludedStatuses
    );


}
