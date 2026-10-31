package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.RandomBloodSugar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RandomBloodSugarRepository extends JpaRepository<RandomBloodSugar, Long> {

    Optional<RandomBloodSugar> findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(Long encounterId);

    Optional<RandomBloodSugar> findFirstByEncounterIdAndIsActiveTrueAndCreatedDateBetweenOrderByCreatedDateDesc(
            Long encounterId,
            Instant dayStart,
            Instant dayEnd
    );

    List<RandomBloodSugar> findByEncounterIdOrderByCreatedDateAscIdAsc(Long encounterId);
}
