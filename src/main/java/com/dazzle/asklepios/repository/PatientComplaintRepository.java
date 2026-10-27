package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientComplaint;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientComplaintRepository
    extends JpaRepository<PatientComplaint, Long>, JpaSpecificationExecutor<PatientComplaint> {

    boolean existsByComplaintReference(String complaintReference);

    @Query(value = "SELECT nextval('patient_complaint_reference_seq')", nativeQuery = true)
    Long nextReferenceSequence();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PatientComplaint c where c.id = :id")
    Optional<PatientComplaint> findByIdForUpdate(@Param("id") Long id);

    @Query(
        """
        select distinct c from PatientComplaint c
        left join fetch c.actions
        where c.id = :id
        """
    )
    Optional<PatientComplaint> findByIdWithActions(@Param("id") Long id);

    long countByFacilityIdAndStatus(Long facilityId, PatientComplaintStatus status);

    long countByFacilityIdAndAssignedUserIdIsNull(Long facilityId);

    long countByFacilityIdAndAssignedUserId(Long facilityId, Long assignedUserId);

    @Query(
        """
        select count(c) from PatientComplaint c
        where c.facilityId = :facilityId
          and c.targetDeadline is not null
          and c.targetDeadline < :now
          and c.status not in (com.dazzle.asklepios.domain.enumeration.PatientComplaintStatus.RESOLVED,
                               com.dazzle.asklepios.domain.enumeration.PatientComplaintStatus.CLOSED)
        """
    )
    long countOverdueByFacilityId(@Param("facilityId") Long facilityId, @Param("now") Instant now);
}
