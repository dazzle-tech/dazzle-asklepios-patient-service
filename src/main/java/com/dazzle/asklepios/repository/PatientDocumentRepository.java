package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientDocumentRepository extends JpaRepository<PatientDocument, Long> {

    Page<PatientDocument> findByPatientId(Long patientId, Pageable pageable);

    Page<PatientDocument> findByIsPrimaryTrueAndNumberContainingIgnoreCase(
        String numberPart,
        Pageable pageable
    );

    Page<PatientDocument> findByNumberContainingIgnoreCase(String numberPart, Pageable pageable);

    Optional<PatientDocument> findFirstByPatient_IdAndIsPrimaryTrue(Long patientId);

    Optional<PatientDocument> findFirstByPatient_IdOrderByIdAsc(Long patientId);

    Optional<PatientDocument> findByPatientIdAndIsPrimaryTrue(Long patientId);

    Optional<PatientDocument> findFirstByNumberIgnoreCase(String number);

    List<PatientDocument> findByPatient_IdOrderByIsPrimaryDescIdAsc(Long patientId);

    boolean existsByPatientId(Long patientId);

    Optional<PatientDocument> findByNumberAndIsPrimaryTrue(String number);

    /**
     * Exact type + case-insensitive number match (primary and non-primary).
     * EntityGraph loads patient for callers that map contact fields in the same transaction.
     */
    @EntityGraph(attributePaths = "patient")
    List<PatientDocument> findByTypeAndNumberIgnoreCase(DocumentType type, String number);

    Optional<PatientDocument> getDocumentByNumber(String number);

    List<PatientDocument> findByPatientIdAndType(Long patientId, FinancialDocumentType type);
}
