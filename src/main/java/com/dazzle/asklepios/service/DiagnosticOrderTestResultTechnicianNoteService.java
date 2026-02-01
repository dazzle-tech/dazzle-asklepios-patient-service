package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTestResultTechnicianNote;

import com.dazzle.asklepios.repository.DiagnosticOrderTestResultTechnicianNoteRepository;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.resulttechniciannote.DiagnosticOrderTestResultTechnicianNoteDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DiagnosticOrderTestResultTechnicianNoteService {

    private final DiagnosticOrderTestResultTechnicianNoteRepository noteRepository;

    public DiagnosticOrderTestResultTechnicianNoteService(DiagnosticOrderTestResultTechnicianNoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    public DiagnosticOrderTestResultTechnicianNote create(DiagnosticOrderTestResultTechnicianNoteDTO dto) {
        DiagnosticOrderTestResultTechnicianNote n = new DiagnosticOrderTestResultTechnicianNote();
        n.setOrderTestId(dto.orderTestId());
        n.setResultId(dto.resultId());
        n.setNote(dto.note());
        return noteRepository.save(n);
    }

    public void delete(Long id) {
        noteRepository.deleteById(id);
    }
}
