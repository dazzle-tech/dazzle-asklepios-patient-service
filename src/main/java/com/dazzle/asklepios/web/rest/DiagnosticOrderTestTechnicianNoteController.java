
package com.dazzle.asklepios.web.rest;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestTechnicianNote;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestTechnicianNoteRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.DiagnosticOrderTestTechnicianNoteService;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.techniciannotes.DiagnosticOrderTestTechnicianNoteDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.DiagnosticOrderTestTechnicianNoteResponseVM;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestTechnicianNoteController {

    private final DiagnosticOrderTestTechnicianNoteService noteService;
    private final DiagnosticOrderTestTechnicianNoteRepository noteRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    public DiagnosticOrderTestTechnicianNoteController(
            DiagnosticOrderTestTechnicianNoteService noteService,
            DiagnosticOrderTestTechnicianNoteRepository noteRepository,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository
    ) {
        this.noteService = noteService;
        this.noteRepository = noteRepository;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
    }


    @PostMapping("/diagnostic-order-test-notes")
    public ResponseEntity<DiagnosticOrderTestTechnicianNoteResponseVM> create(
            @Valid @RequestBody DiagnosticOrderTestTechnicianNoteDTO dto
    ) {
        // Validate order_test exists
        DiagnosticOrderTest test = diagnosticOrderTestRepository.findById(dto.orderTestId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + dto.orderTestId()
                ));

        // Validate order_id matches the test's order_id
        if (test.getOrderId() == null || !test.getOrderId().equals(dto.orderId())) {
            throw new BadRequestAlertException(
                    "order_mismatch",
                    "diagnostic_order_test_technician_notes",
                    "orderId does not match the order of the given orderTestId"
            );
        }


        DiagnosticOrderTestTechnicianNote saved = noteService.create(dto);

        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-order-test-notes/" + saved.getId()))
                .body(DiagnosticOrderTestTechnicianNoteResponseVM.ofEntity(saved));
    }

    @GetMapping("/diagnostic-order-test-notes/{id}")
    public ResponseEntity<DiagnosticOrderTestTechnicianNoteResponseVM> getById(@PathVariable Long id) {
        DiagnosticOrderTestTechnicianNote existing = noteRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_test_technician_notes",
                        "Note not found with id " + id
                ));
        return ResponseEntity.ok(DiagnosticOrderTestTechnicianNoteResponseVM.ofEntity(existing));
    }

    @GetMapping("/diagnostic-order-tests/{orderTestId}/notes")
    public ResponseEntity<List<DiagnosticOrderTestTechnicianNoteResponseVM>> listByOrderTestId(
            @PathVariable Long orderTestId,
            @ParameterObject Pageable pageable
    ) {
        Page<DiagnosticOrderTestTechnicianNote> page = noteRepository.findByOrderTestId(orderTestId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderTestTechnicianNoteResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderTestTechnicianNoteResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/diagnostic-orders/{orderId}/notes")
    public ResponseEntity<List<DiagnosticOrderTestTechnicianNoteResponseVM>> listByOrderId(
            @PathVariable Long orderId,
            @ParameterObject Pageable pageable
    ) {
        Page<DiagnosticOrderTestTechnicianNote> page = noteRepository.findByOrderId(orderId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderTestTechnicianNoteResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticOrderTestTechnicianNoteResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @DeleteMapping("/diagnostic-order-test-notes/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        DiagnosticOrderTestTechnicianNote existing = noteRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_test_technician_notes",
                        "Note not found with id " + id
                ));

        noteService.delete(existing.getId());
        return ResponseEntity.noContent().build();
    }
}
