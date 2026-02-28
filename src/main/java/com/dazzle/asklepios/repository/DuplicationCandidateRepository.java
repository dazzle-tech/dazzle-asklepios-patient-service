package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DuplicationCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DuplicationCandidateRepository
        extends JpaRepository<DuplicationCandidate, Long> {

    Optional<DuplicationCandidate> findByIdAndIsActiveTrue(Long id);
}