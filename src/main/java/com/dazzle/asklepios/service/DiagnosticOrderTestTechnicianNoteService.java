package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTestTechnicianNote;
import com.dazzle.asklepios.repository.DiagnosticOrderTestTechnicianNoteRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.techniciannotes.DiagnosticOrderTestTechnicianNoteDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DiagnosticOrderTestTechnicianNoteService {

    private final DiagnosticOrderTestTechnicianNoteRepository noteRepository;

    public DiagnosticOrderTestTechnicianNoteService(DiagnosticOrderTestTechnicianNoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    public DiagnosticOrderTestTechnicianNote create(DiagnosticOrderTestTechnicianNoteDTO dto) {
        DiagnosticOrderTestTechnicianNote n = new DiagnosticOrderTestTechnicianNote();
        n.setOrderTestId(dto.orderTestId());
        n.setOrderId(dto.orderId());
        n.setNote(dto.note());
        return noteRepository.save(n);
    }

    public void delete(Long id) {
        noteRepository.deleteById(id);
    }
}
