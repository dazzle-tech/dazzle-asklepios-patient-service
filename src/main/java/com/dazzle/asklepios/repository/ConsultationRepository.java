package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.Consultation;
import com.dazzle.asklepios.domain.enumeration.ConsultationStatus;
import com.dazzle.asklepios.domain.enumeration.DestinationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ConsultationRepository
        extends JpaRepository<Consultation, Long> {

    Page<Consultation> findByEncounterIdOrderByCreatedDateDesc(
            Long encounterId,
            Pageable pageable
    );

    Page<Consultation> findByEncounterIdAndStatusNotOrderByCreatedDateDesc(
            Long encounterId,
            ConsultationStatus status,
            Pageable pageable
    );

    Page<Consultation> findByEncounterIdAndCreatedDateBetweenOrderByCreatedDateDesc(
            Long encounterId,
            Instant from,
            Instant to,
            Pageable pageable
    );

    Page<Consultation> findByEncounterIdAndCreatedDateBetweenAndStatusNotOrderByCreatedDateDesc(
            Long encounterId,
            Instant from,
            Instant to,
            ConsultationStatus status,
            Pageable pageable
    );

    List<Consultation> findByEncounterIdAndDestinationType(
            Long encounterId,
            DestinationType destinationType
    );



    Page<Consultation>
    findByCreatedDateBetweenAndFromFacilityIdAndPractitionerIdAndStatusNotIn(
            Instant fromDate,
            Instant toDate,
            Long fromFacilityId,
            Long practitionerId,
            List<ConsultationStatus> excludedStatuses,
            Pageable pageable
    );

    Page<Consultation>
    findByCreatedDateBetweenAndFromFacilityIdAndPractitionerIdAndFromDepartmentIdInAndStatusNotIn(
            Instant fromDate,
            Instant toDate,
            Long fromFacilityId,
            Long practitionerId,
            List<Long> fromDepartmentIds,
            List<ConsultationStatus> excludedStatuses,
            Pageable pageable
    );

    Page<Consultation>
    findByCreatedDateBetweenAndFromFacilityIdAndToDepartmentIdAndStatusNotIn(
            Instant fromDate,
            Instant toDate,
            Long fromFacilityId,
            Long toDepartmentId,
            List<ConsultationStatus> excludedStatuses,
            Pageable pageable
    );

    Page<Consultation>
    findByCreatedDateBetweenAndFromFacilityIdAndToDepartmentIdAndFromDepartmentIdInAndStatusNotIn(
            Instant fromDate,
            Instant toDate,
            Long fromFacilityId,
            Long toDepartmentId,
            List<Long> fromDepartmentIds,
            List<ConsultationStatus> excludedStatuses,
            Pageable pageable
    );



}
