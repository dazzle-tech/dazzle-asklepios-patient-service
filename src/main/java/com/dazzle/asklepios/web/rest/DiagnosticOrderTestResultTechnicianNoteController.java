package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResultTechnicianNote;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultTechnicianNoteRepository;
import com.dazzle.asklepios.service.DiagnosticOrderTestResultTechnicianNoteService;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.resulttechniciannote.DiagnosticOrderTestResultTechnicianNoteDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.techniciannotes.DiagnosticOrderTestTechnicianNoteDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.laboratory.DiagnosticOrderTestResultTechnicianNoteResponseVM;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestResultTechnicianNoteController {

    private final DiagnosticOrderTestResultTechnicianNoteService noteService;
    private final DiagnosticOrderTestResultTechnicianNoteRepository noteRepository;
    private final DiagnosticOrderTestResultRepository diagnosticOrderTestResultRepository;

    public DiagnosticOrderTestResultTechnicianNoteController(
            DiagnosticOrderTestResultTechnicianNoteService noteService,
            DiagnosticOrderTestResultTechnicianNoteRepository noteRepository,
            DiagnosticOrderTestResultRepository diagnosticOrderTestResultRepository
    ) {
        this.noteService = noteService;
        this.noteRepository = noteRepository;
        this.diagnosticOrderTestResultRepository = diagnosticOrderTestResultRepository;
    }

    @PostMapping("/diagnostic-order-test-result-notes")
    public ResponseEntity<DiagnosticOrderTestResultTechnicianNoteResponseVM> create(
            @Valid @RequestBody DiagnosticOrderTestResultTechnicianNoteDTO dto
    ) {

        DiagnosticOrderTestResult testResult =
                diagnosticOrderTestResultRepository.findById(dto.resultId())
                        .orElseThrow(() -> new BadRequestAlertException(
                                "notfound",
                                "diagnostic_order_test_results",
                                "DiagnosticOrderTestResult not found with id " + dto.resultId()
                        ));
        if (!testResult.getOrderTestId().equals(dto.orderTestId())) {
            throw new BadRequestAlertException(
                    "order_test_mismatch",
                    "diagnostic_order_test_result_technician_notes",
                    "orderTestId does not match the given resultId"
            );
        }


        DiagnosticOrderTestResultTechnicianNote saved = noteService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-order-test-result-notes/" + saved.getId()))
                .body(DiagnosticOrderTestResultTechnicianNoteResponseVM.ofEntity(saved));
    }


    @GetMapping("/diagnostic-order-test-result-notes/{id}")
    public ResponseEntity<DiagnosticOrderTestResultTechnicianNoteResponseVM> getById(@PathVariable Long id) {
        DiagnosticOrderTestResultTechnicianNote existing = noteRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_test_result_technician_notes",
                        "Note not found with id " + id
                ));

        return ResponseEntity.ok(DiagnosticOrderTestResultTechnicianNoteResponseVM.ofEntity(existing));
    }

    // List notes by orderTestId (no pagination)
    @GetMapping("/diagnostic-order-tests/{orderTestId}/result-notes")
    public ResponseEntity<List<DiagnosticOrderTestResultTechnicianNoteResponseVM>> listByOrderTestId(
            @PathVariable Long orderTestId
    ) {
        List<DiagnosticOrderTestResultTechnicianNoteResponseVM> body = noteRepository.findByOrderTestId(orderTestId)
                .stream()
                .map(DiagnosticOrderTestResultTechnicianNoteResponseVM::ofEntity)
                .toList();

        return ResponseEntity.ok(body);
    }

    // (Optional) List notes by resultId (no pagination) if you want this endpoint too
    @GetMapping("/diagnostic-order-test-results/{resultId}/notes")
    public ResponseEntity<List<DiagnosticOrderTestResultTechnicianNoteResponseVM>> listByResultId(
            @PathVariable Long resultId
    ) {
        List<DiagnosticOrderTestResultTechnicianNoteResponseVM> body = noteRepository.findByResultId(resultId)
                .stream()
                .map(DiagnosticOrderTestResultTechnicianNoteResponseVM::ofEntity)
                .toList();

        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/diagnostic-order-test-result-notes/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        DiagnosticOrderTestResultTechnicianNote existing = noteRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_test_result_technician_notes",
                        "Note not found with id " + id
                ));

        noteService.delete(existing.getId());
        return ResponseEntity.noContent().build();
    }
}
