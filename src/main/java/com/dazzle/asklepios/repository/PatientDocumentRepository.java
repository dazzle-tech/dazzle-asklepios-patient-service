package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PatientDocumentRepository extends JpaRepository<PatientDocument, Long> {

    Page<PatientDocument> findByPatientIdAndCategory(
            Long patientId,
            DocumentCategory category,
            Pageable pageable
    );

    List<PatientDocument> findByPatientIdAndCategory(
            Long patientId,
            DocumentCategory category
    );
}