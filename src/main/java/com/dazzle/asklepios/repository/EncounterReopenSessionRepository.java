package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterReopenSession;
import com.dazzle.asklepios.domain.enumeration.EncounterReopenSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("""
            select session
            from EncounterReopenSession session
            join fetch session.encounter encounter
            where encounter.id in :encounterIds
            """)
    List<EncounterReopenSession> findByEncounter_IdIn(@Param("encounterIds") Collection<Long> encounterIds);

    @Query("""
            select coalesce(max(session.sessionNumber), 0)
            from EncounterReopenSession session
            where session.encounter.id = :encounterId
            """)
    int findMaxSessionNumberByEncounterId(@Param("encounterId") Long encounterId);
}
