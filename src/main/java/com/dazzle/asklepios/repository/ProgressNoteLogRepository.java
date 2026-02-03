package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ProgressNoteLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgressNoteLogRepository
        extends JpaRepository<ProgressNoteLog, Long> {

    List<ProgressNoteLog> findByProgressNoteIdOrderByCreatedDateDesc(Long progressNoteId);
}
