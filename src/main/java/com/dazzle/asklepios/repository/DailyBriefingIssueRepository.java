package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DailyBriefingIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyBriefingIssueRepository extends JpaRepository<DailyBriefingIssue, Long> {

    @Query("""
            select i from DailyBriefingIssue i
            where i.facilityId = :facilityId
              and i.reportDate = :reportDate
              and i.recipient = :recipient
              and i.language = :language
              and i.superseded = false
              and (i.departmentId = :departmentId or (i.departmentId is null and :departmentId is null))
              and (i.practitionerId = :practitionerId or (i.practitionerId is null and :practitionerId is null))
              and (i.specialty = :specialty or (i.specialty is null and :specialty is null))
            """)
    Optional<DailyBriefingIssue> findActive(
            @Param("facilityId") Long facilityId,
            @Param("departmentId") Long departmentId,
            @Param("practitionerId") Long practitionerId,
            @Param("specialty") String specialty,
            @Param("reportDate") LocalDate reportDate,
            @Param("recipient") String recipient,
            @Param("language") String language
    );
}
