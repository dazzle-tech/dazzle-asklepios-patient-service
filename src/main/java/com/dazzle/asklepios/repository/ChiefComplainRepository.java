package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ChiefComplain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChiefComplainRepository extends JpaRepository<ChiefComplain, Long> {
        Optional<ChiefComplain> findTopByEncounterIdOrderByCreatedDateDesc(Long encounterId);
}