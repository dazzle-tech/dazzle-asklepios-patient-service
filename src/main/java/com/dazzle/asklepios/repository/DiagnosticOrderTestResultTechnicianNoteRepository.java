package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTestResultTechnicianNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiagnosticOrderTestResultTechnicianNoteRepository
        extends JpaRepository<DiagnosticOrderTestResultTechnicianNote, Long> {

    List<DiagnosticOrderTestResultTechnicianNote> findByOrderTestId(Long orderTestId);

    List<DiagnosticOrderTestResultTechnicianNote> findByResultId(Long resultId);
}
