package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientActivity;
import com.dazzle.asklepios.domain.enumeration.PatientActivityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PatientActivityRepository extends JpaRepository<PatientActivity, Long>, JpaSpecificationExecutor<PatientActivity> {

    long countByPatientIdAndStatus(Long patientId, PatientActivityStatus status);

    long countByPatientIdAndStatusAndDueAtBefore(Long patientId, PatientActivityStatus status, Instant dueAt);

    long countByPatientIdAndStatusAndDueAtGreaterThanEqualAndDueAtLessThan(
            Long patientId,
            PatientActivityStatus status,
            Instant from,
            Instant to
    );

    long countByPatientIdAndStatusAndCompletedAtGreaterThanEqual(
            Long patientId,
            PatientActivityStatus status,
            Instant from
    );

    boolean existsBySourceTypeAndSourceIdAndActivityType(String sourceType, Long sourceId, String activityType);

    /** Open tasks due before the given time, overdue ones first. */
    List<PatientActivity> findByStatusAndDueAtBeforeOrderByDueAtAsc(PatientActivityStatus status, Instant dueAt);

    List<PatientActivity> findByIdIn(List<Long> ids);
}
