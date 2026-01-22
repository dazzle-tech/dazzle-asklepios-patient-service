package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.TelephonicConsultation;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelephonicConsultationRepository
        extends JpaRepository<TelephonicConsultation, Long> {

    Page<TelephonicConsultation> findByEncounterIdAndStatus(
            Long encounterId,
            DiagnosticStatus status,
            Pageable pageable
    );

    Page<TelephonicConsultation> findByEncounterIdAndStatusNot(
            Long encounterId,
            DiagnosticStatus status,
            Pageable pageable
    );
}
