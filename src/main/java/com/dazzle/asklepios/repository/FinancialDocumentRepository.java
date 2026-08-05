package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.List;

@Repository
public interface FinancialDocumentRepository extends JpaRepository<FinancialDocument, Long> {

    // ✅ find document by encounter (أهم method عندك الآن ✅)
    Optional<FinancialDocument> findByEncounterId(Long encounterId);

    // ✅ لو بدك أكثر من document لنفس encounter
    List<FinancialDocument> findAllByEncounterId(Long encounterId);

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
}