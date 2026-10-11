package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterReopenSession;
import com.dazzle.asklepios.domain.enumeration.EncounterReopenSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EncounterReopenSessionRepository extends JpaRepository<EncounterReopenSession, Long> {

    Optional<EncounterReopenSession> findByEncounter_IdAndStatus(
            Long encounterId,
            EncounterReopenSessionStatus status
    );

    boolean existsByEncounter_IdAndStatus(Long encounterId, EncounterReopenSessionStatus status);

    List<EncounterReopenSession> findByEncounter_IdOrderBySessionNumberAsc(Long encounterId);

    List<EncounterReopenSession> findByEncounter_IdOrderBySessionNumberDesc(Long encounterId);

    Page<EncounterReopenSession> findByEncounter_IdOrderBySessionNumberDesc(Long encounterId, Pageable pageable);

    @EntityGraph(attributePaths = "encounter")
    List<EncounterReopenSession> findByEncounter_IdIn(Collection<Long> encounterIds);

    Optional<EncounterReopenSession> findTopByEncounter_IdOrderBySessionNumberDesc(Long encounterId);
}
