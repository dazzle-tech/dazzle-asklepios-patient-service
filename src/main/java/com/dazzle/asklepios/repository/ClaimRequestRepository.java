package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClaimRequestRepository extends JpaRepository<ClaimRequest, Long> {

    List<ClaimRequest> findByEncounterIdOrderByIdDesc(Long encounterId);

    List<ClaimRequest> findByPatientIdOrderByIdDesc(Long patientId);

    Optional<ClaimRequest> findFirstByFinancialDocumentIdOrderByIdDesc(Long financialDocumentId);

    boolean existsByFinancialDocumentIdAndStatusIn(Long financialDocumentId, List<ClaimStatus> statuses);

    @Query("""
            SELECT pi.payorId, cr.status, COUNT(cr)
            FROM ClaimRequest cr, PatientInsurance pi
            WHERE pi.id = cr.patientInsuranceId
              AND pi.payorId IS NOT NULL
              AND (:facilityId IS NULL OR EXISTS (
                  SELECT 1 FROM PatientEncounter e
                  WHERE e.id = cr.encounterId AND e.facilityId = :facilityId
              ))
            GROUP BY pi.payorId, cr.status
            """)
    List<Object[]> countClaimsByPayerAndStatus(
            @Param("facilityId") Long facilityId
    );
}
