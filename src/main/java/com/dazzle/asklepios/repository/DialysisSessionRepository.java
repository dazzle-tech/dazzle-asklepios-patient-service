package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DialysisSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DialysisSessionRepository
        extends JpaRepository<DialysisSession, Long> {

    Optional<DialysisSession>
    findFirstByPatientIdAndEncounterIdAndIsActiveTrue(
            Long patientId,
            Long encounterId
    );
}
