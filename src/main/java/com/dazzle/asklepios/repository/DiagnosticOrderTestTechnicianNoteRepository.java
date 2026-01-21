package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTestTechnicianNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosticOrderTestTechnicianNoteRepository
        extends JpaRepository<DiagnosticOrderTestTechnicianNote, Long> {

    Page<DiagnosticOrderTestTechnicianNote> findByOrderTestId(Long orderTestId, Pageable pageable);

    Page<DiagnosticOrderTestTechnicianNote> findByOrderId(Long orderId, Pageable pageable);
}
