package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestTechnicianNote;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestTechnicianNoteRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.techniciannotes.DiagnosticOrderTestTechnicianNoteDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DiagnosticOrderTestTechnicianNoteService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestTechnicianNoteService.class);

    private final DiagnosticOrderTestTechnicianNoteRepository noteRepository;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    public DiagnosticOrderTestTechnicianNoteService(DiagnosticOrderTestTechnicianNoteRepository noteRepository, DiagnosticOrderRepository diagnosticOrderRepository, DiagnosticOrderTestRepository diagnosticOrderTestRepository) {
        this.noteRepository = noteRepository;
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
    }

    public DiagnosticOrderTestTechnicianNote create(DiagnosticOrderTestTechnicianNoteDTO dto) {
        LOG.debug("[TechnicianNoteService] CREATE - start. payload={}", dto);

        DiagnosticOrderTestTechnicianNote note =
                new DiagnosticOrderTestTechnicianNote();

        DiagnosticOrder diagnosticOrder =
                getDiagnosticOrder(dto.orderId());

        DiagnosticOrderTest diagnosticOrderTest =
                getDiagnosticOrderTest(dto.orderTestId());

        note.setOrderId(diagnosticOrder.getId());
        note.setOrderTestId(diagnosticOrderTest.getId());
        note.setNote(dto.note());

        DiagnosticOrderTestTechnicianNote saved =
                noteRepository.save(note);

        LOG.debug(
                "[TechnicianNoteService] CREATE - done. id={} orderId={} orderTestId={}",
                saved.getId(),
                saved.getOrderId(),
                saved.getOrderTestId()
        );

        return saved;
    }

    public void delete(Long id) {
        LOG.debug("[TechnicianNoteService] DELETE - start. id={}", id);
        noteRepository.deleteById(id);
        LOG.debug("[TechnicianNoteService] DELETE - done. id={}", id);
    }

    private DiagnosticOrder getDiagnosticOrder(Long diagnosticOrderId) {
        LOG.debug("[TechnicianNoteService]  getDiagnosticOrder:  id={}", diagnosticOrderId);

        return diagnosticOrderRepository.findById(diagnosticOrderId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_orders",
                        "Order not found with id " + diagnosticOrderId
                ));
    }

    private DiagnosticOrderTest getDiagnosticOrderTest(Long diagnosticOrderTestId) {
        LOG.debug("[TechnicianNoteService]  getDiagnosticOrderTest:  id={}", diagnosticOrderTestId);

        return diagnosticOrderTestRepository.findById(diagnosticOrderTestId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnosticOrdersTest",
                        "Test not found with id " + diagnosticOrderTestId
                ));
    }

}
