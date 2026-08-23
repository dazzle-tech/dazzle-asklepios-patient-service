package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

@Repository
public interface FinancialDocumentRepository extends JpaRepository<FinancialDocument, Long> {

    // ✅ find document by encounter (أهم method عندك الآن ✅)
    Optional<FinancialDocument> findByEncounterId(Long encounterId);

    // ✅ لو بدك أكثر من document لنفس encounter
    List<FinancialDocument> findAllByEncounterId(Long encounterId);

    List<FinancialDocument> findByEncounterIdIn(Collection<Long> encounterIds);

    // ✅ find by patient
    List<FinancialDocument> findByPatientId(Long patientId);

    // ✅ find by document number (invoice number)
    Optional<FinancialDocument> findByDocumentNumber(String documentNumber);

   Optional<FinancialDocument> findFirstByEncounterIdAndDocumentTypeOrderByIdDesc(Long encounterId, FinancialDocumentType documentType);
    Optional<FinancialDocument> findByEncounterIdAndDocumentType(
            Long encounterId,
            FinancialDocumentType type
    );
    List<FinancialDocument> findAllByParentDocumentId(Long parentDocumentId);
    boolean existsByEncounterIdAndDocumentType(
            Long encounterId,
            FinancialDocumentType type
    );

    boolean existsByEncounterIdAndDocumentTypeAndStatusIn(
            Long encounterId,
            FinancialDocumentType documentType,
            Collection<FinancialDocumentStatus> statuses
    );

    List<FinancialDocument> findAllByPatientIdAndDocumentTypeOrderByCreatedDateDesc(
            Long patientId,
            FinancialDocumentType documentType
    );

    List<FinancialDocument> findAllByPatientIdOrderByCreatedDateDesc(Long patientId);

    boolean existsByEncounterIdAndDocumentTypeAndDocumentSubtypeAndStatusIn(
            Long encounterId,
            FinancialDocumentType documentType,
            FinancialDocumentSubtype documentSubtype,
            Collection<FinancialDocumentStatus> statuses
    );

    Optional<FinancialDocument> findFirstByEncounterIdAndDocumentTypeAndDocumentSubtypeOrderByIdDesc(
            Long encounterId,
            FinancialDocumentType documentType,
            FinancialDocumentSubtype documentSubtype
    );

    @Query(
            value = """
                    SELECT MAX(
                        CAST(
                            SUBSTRING(fd.document_number FROM '([0-9]+)$')
                            AS BIGINT
                        )
                    )
                    FROM financial_documents fd
                    INNER JOIN patient_encounters pe ON pe.id = fd.encounter_id
                    WHERE pe.facility_id = :facilityId
                      AND fd.document_type = :documentType
                      AND EXTRACT(YEAR FROM fd.created_date) = :year
                    """,
            nativeQuery = true
    )
    Optional<Long> findMaxIssuedSequenceForYear(
            @Param("facilityId") Long facilityId,
            @Param("documentType") String documentType,
            @Param("year") int year
    );

    @Query("""
            SELECT fd FROM FinancialDocument fd
            JOIN PatientEncounter e ON e.id = fd.encounterId
            JOIN PatientInsurance pi ON pi.id = e.patientInsuranceId
            WHERE fd.documentType = com.dazzle.asklepios.domain.enumeration.FinancialDocumentType.INVOICE
              AND fd.documentSubtype = com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype.INSURANCE_CLAIM
              AND fd.status IN :statuses
              AND (:payorId IS NULL OR pi.payorId = :payorId)
              AND (:fromDate IS NULL OR fd.createdDate >= :fromDate)
              AND (:toDate IS NULL OR fd.createdDate < :toDate)
              AND NOT EXISTS (
                  SELECT 1 FROM ClaimRequest cr
                  WHERE cr.financialDocumentId = fd.id
                    AND cr.status IN :activeClaimStatuses
              )
            ORDER BY fd.createdDate DESC
            """)
    List<FinancialDocument> findPendingInsuranceClaimInvoices(
            @Param("payorId") Long payorId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("statuses") Collection<FinancialDocumentStatus> statuses,
            @Param("activeClaimStatuses") Collection<ClaimStatus> activeClaimStatuses
    );
}