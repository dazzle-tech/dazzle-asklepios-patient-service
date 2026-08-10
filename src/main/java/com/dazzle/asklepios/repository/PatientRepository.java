package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {


    Page<Patient> findByMedicalRecordNumberContainingIgnoreCase(String medicalRecordNumber, Pageable pageable);

    Page<Patient> findByArchivingNumberContainingIgnoreCase(String archivingNumber, Pageable pageable);

    Page<Patient> findByPrimaryMobileNumberContaining(String primaryPhoneNumber, Pageable pageable);

    Page<Patient> findByDateOfBirth(LocalDate dateOfBirth, Pageable pageable);

    Page<Patient> findByIsUnknownTrue(Pageable pageable);

    Page<Patient> findDistinctByPatientDocuments_NumberContainingIgnoreCase(String numberPart, Pageable pageable);

    Optional<Patient> findByPreviousId(String previousId);

    Optional<Patient> findOneByResetKey(String resetKey);

    Optional<Patient> findByMedicalRecordNumber(String medicalRecordNumber);

    Optional<Patient> findByDocumentIdIgnoreCase(String documentId);
    List<Patient> getPatientByPin(String pin);

}