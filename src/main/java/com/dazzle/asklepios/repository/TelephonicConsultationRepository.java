package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.TelephonicConsultation;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface TelephonicConsultationRepository
        extends JpaRepository<TelephonicConsultation, Long> {

    Page<TelephonicConsultation> findByEncounterId(
            Long encounterId,
            Pageable pageable
    );

    Page<TelephonicConsultation> findByEncounterIdAndStatusNot(
            Long encounterId,
            DiagnosticStatus status,
            Pageable pageable
    );

    Page<TelephonicConsultation> findByEncounterIdAndCreatedDateBetween(
            Long encounterId,
            Instant from,
            Instant to,
            Pageable pageable
    );

    Page<TelephonicConsultation> findByEncounterIdAndCreatedDateBetweenAndStatusNot(
            Long encounterId,
            Instant from,
            Instant to,
            DiagnosticStatus status,
            Pageable pageable
    );
    Page<TelephonicConsultation> findByEncounterIdAndCreatedDateAfter(
            Long encounterId,
            Instant fromDate,
            Pageable pageable
    );

    Page<TelephonicConsultation> findByEncounterIdAndCreatedDateBefore(
            Long encounterId,
            Instant toDate,
            Pageable pageable
    );

    Page<TelephonicConsultation> findByEncounterIdAndCreatedDateAfterAndStatusNot(
            Long encounterId,
            Instant fromDate,
            DiagnosticStatus status,
            Pageable pageable
    );

    Page<TelephonicConsultation> findByEncounterIdAndCreatedDateBeforeAndStatusNot(
            Long encounterId,
            Instant toDate,
            DiagnosticStatus status,
            Pageable pageable
    );

}
