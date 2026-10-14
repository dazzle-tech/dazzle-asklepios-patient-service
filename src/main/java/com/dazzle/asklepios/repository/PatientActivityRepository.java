package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientActivity;
import com.dazzle.asklepios.domain.enumeration.PatientActivityCategory;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientActivityRepository extends JpaRepository<PatientActivity, Long> {

    Page<PatientActivity> findByPatientId(Long patientId, Pageable pageable);

    Page<PatientActivity> findByPatientIdAndCategory(
            Long patientId,
            PatientActivityCategory category,
            Pageable pageable
    );

    Page<PatientActivity> findByPatientIdAndStatus(
            Long patientId,
            PatientActivityStatus status,
            Pageable pageable
    );

    Page<PatientActivity> findByPatientIdAndCategoryAndStatus(
            Long patientId,
            PatientActivityCategory category,
            PatientActivityStatus status,
            Pageable pageable
    );
}
