package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @Query(
            value = "SELECT MAX((SUBSTRING(p.mrn, 2))::INTEGER) FROM patients p",
            nativeQuery = true
    )
    Integer findMaxMrnNumber();

    Page<Patient> findByMrnContainingIgnoreCase(String mrn, Pageable pageable);

    Page<Patient> findByArchivingNumberContainingIgnoreCase(String archivingNumber, Pageable pageable);

    Page<Patient> findByPrimaryMobileNumberContaining(String primaryPhoneNumber, Pageable pageable);

    Page<Patient> findByDateOfBirth(LocalDate dateOfBirth, Pageable pageable);

    Page<Patient>
    findByFirstNameContainingIgnoreCaseOrSecondNameContainingIgnoreCaseOrThirdNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
            String firstName,
            String secondName,
            String thirdName,
            String lastName,
            Pageable pageable
    );
}
