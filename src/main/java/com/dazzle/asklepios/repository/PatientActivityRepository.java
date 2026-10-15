package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientActivity;
import com.dazzle.asklepios.domain.enumeration.PatientActivityCategory;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import com.dazzle.asklepios.domain.enumeration.PatientActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface PatientActivityRepository extends JpaRepository<PatientActivity, Long>, JpaSpecificationExecutor<PatientActivity> {

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

    long countByPatientIdAndStatus(Long patientId, PatientActivityStatus status);

    long countByPatientIdAndStatusAndDueAtBefore(Long patientId, PatientActivityStatus status, Instant dueAt);

    boolean existsBySourceTypeAndSourceIdAndActivityType(
            String sourceType,
            Long sourceId,
            PatientActivityType activityType
    );
}
