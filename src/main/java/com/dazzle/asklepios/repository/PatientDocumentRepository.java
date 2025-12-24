package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientDocumentRepository extends JpaRepository<PatientDocument, Long> {

    Page<PatientDocument> findByPatientId(Long patientId, Pageable pageable);

    Page<PatientDocument> findByIsPrimaryTrueAndNumberContainingIgnoreCase(
            String numberPart,
            Pageable pageable
    );

    Page<PatientDocument> findByNumberContainingIgnoreCase(String numberPart, Pageable pageable);


}
