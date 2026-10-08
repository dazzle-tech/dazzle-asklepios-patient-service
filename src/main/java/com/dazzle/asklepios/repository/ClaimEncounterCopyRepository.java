package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimEncounterCopy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClaimEncounterCopyRepository extends JpaRepository<ClaimEncounterCopy, Long> {

    Optional<ClaimEncounterCopy> findByEncounterId(Long encounterId);
}