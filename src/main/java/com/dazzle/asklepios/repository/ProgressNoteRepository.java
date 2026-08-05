package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ProgressNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProgressNoteRepository
        extends JpaRepository<ProgressNote, Long> {


    Page<ProgressNote> findByEncounterId(
            Long encounterId,
            Pageable pageable
    );


    Page<ProgressNote> findByEncounterIdAndCancelledDateIsNull(
            Long encounterId,
            Pageable pageable
    );

    Optional<ProgressNote> findTopByEncounterIdAndCancelledDateIsNullOrderByCreatedDateDesc(Long encounterId);
}
