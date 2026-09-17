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

    @Query(
            value = """
                    SELECT fd.*
                    FROM financial_documents fd
                    INNER JOIN patient_encounters pe ON pe.id = fd.encounter_id
                    INNER JOIN patient_insurances pi ON pi.id = pe.patient_insurance_id
                    WHERE fd.document_type = 'INVOICE'
                      AND fd.document_subtype = 'INSURANCE_CLAIM'
                      AND fd.status IN (:statuses)
                      AND (
                            (:useNphiesFilter = 1 AND LOWER(pi.payer_nphies_id) IN (:payerNphiesIds))
                            OR
                            (:useNphiesFilter = 0 AND pi.payor_id = :payorId)
                          )
                      AND fd.created_date >= :fromDate
                      AND fd.created_date < :toDate
                      AND NOT EXISTS (
                            SELECT 1
                            FROM claim_request cr
                            WHERE cr.financial_document_id = fd.id
                              AND cr.status IN (:activeClaimStatuses)
                              AND (
                                    cr.claim_type = :claimType
                                    OR (
                                        :claimType = 'PROFESSIONAL'
                                        AND cr.claim_type IS NULL
                                    )
                                  )
                          )
                    ORDER BY fd.created_date DESC
                    """,
            nativeQuery = true
    )
    List<FinancialDocument> findPendingInsuranceClaimInvoices(
            @Param("useNphiesFilter") int useNphiesFilter,
            @Param("payorId") Long payorId,
            @Param("payerNphiesIds") Collection<String> payerNphiesIds,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            @Param("statuses") Collection<String> statuses,
            @Param("activeClaimStatuses") Collection<String> activeClaimStatuses,
            @Param("claimType") String claimType
    );
}