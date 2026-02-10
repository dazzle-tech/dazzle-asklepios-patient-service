package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTestTechnicianNote;
import com.dazzle.asklepios.repository.DiagnosticOrderTestTechnicianNoteRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.techniciannotes.DiagnosticOrderTestTechnicianNoteDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DiagnosticOrderTestTechnicianNoteService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestTechnicianNoteService.class);

    private final DiagnosticOrderTestTechnicianNoteRepository noteRepository;

    public DiagnosticOrderTestTechnicianNoteService(DiagnosticOrderTestTechnicianNoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    public DiagnosticOrderTestTechnicianNote create(DiagnosticOrderTestTechnicianNoteDTO dto) {
        LOG.debug("[TechnicianNoteService] CREATE - start. payload={}", dto);
        DiagnosticOrderTestTechnicianNote note = new DiagnosticOrderTestTechnicianNote();
        note.setOrderTestId(dto.orderTestId());
        note.setOrderId(dto.orderId());
        note.setNote(dto.note());
        DiagnosticOrderTestTechnicianNote saved = noteRepository.save(note);
        LOG.debug("[TechnicianNoteService] CREATE - done. id={} orderId={} orderTestId={}",
                saved.getId(), saved.getOrderId(), saved.getOrderTestId());
        return saved;
    }

    public void delete(Long id) {
        LOG.debug("[TechnicianNoteService] DELETE - start. id={}", id);
        noteRepository.deleteById(id);
        LOG.debug("[TechnicianNoteService] DELETE - done. id={}", id);
    }
}
