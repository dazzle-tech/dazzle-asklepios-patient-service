
package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestTechnicianNote;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestTechnicianNoteRepository;
import com.dazzle.asklepios.service.DiagnosticOrderTestTechnicianNoteService;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.techniciannotes.DiagnosticOrderTestTechnicianNoteDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestTechnicianNoteController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticOrderTestTechnicianNoteController.class);

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
    public ResponseEntity<DiagnosticOrderTestTechnicianNote> create(
            @Valid @RequestBody DiagnosticOrderTestTechnicianNoteDTO dto
    ) {
        LOG.debug("[TechnicianNote] CREATE - request received. payload={}", dto);
        // Validate order_test exists
        DiagnosticOrderTest orderTest= diagnosticOrderTestRepository.findById(dto.orderTestId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_tests",
                        "DiagnosticOrderTest not found with id " + dto.orderTestId()
                ));

        // Validate order_id matches the test's order_id
        if (orderTest.getOrderId() == null || !orderTest.getOrderId().equals(dto.orderId())) {
            throw new BadRequestAlertException(
                    "order_mismatch",
                    "diagnostic_order_test_technician_notes",
                    "orderId does not match the order of the given orderTestId"
            );
        }


        DiagnosticOrderTestTechnicianNote saved = noteService.create(dto);

        LOG.debug("[TechnicianNote] CREATE - created successfully. id={}", saved.getId());
        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-order-test-notes/" + saved.getId()))
                .body(saved);
    }

    @GetMapping("/diagnostic-order-test-notes/{id}")
    public ResponseEntity<DiagnosticOrderTestTechnicianNote> getById(@PathVariable Long id) {
        LOG.debug("[TechnicianNote] GET_BY_ID - request received. id={}", id);
        DiagnosticOrderTestTechnicianNote existing = noteRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_test_technician_notes",
                        "Note not found with id " + id
                ));
        LOG.debug("[TechnicianNote] GET_BY_ID - found. id={}", existing.getId());
        return ResponseEntity.ok(existing);
    }

    @GetMapping("/diagnostic-order-test-notes/by-diagnostic-order-tests/{orderTestId}")
    public ResponseEntity<List<DiagnosticOrderTestTechnicianNote>> listByOrderTestId(
            @PathVariable Long orderTestId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[TechnicianNote] LIST_BY_ORDER_TEST - request received. orderTestId={} pageable={}", orderTestId, pageable);
        Page<DiagnosticOrderTestTechnicianNote> page = noteRepository.findByOrderTestId(orderTestId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderTestTechnicianNote> body = page.getContent();

        LOG.debug("[TechnicianNote] LIST_BY_ORDER_TEST - response ready. orderTestId={} returned={} totalElements={} totalPages={}",
                orderTestId, page.getNumberOfElements(), page.getTotalElements(), page.getTotalPages());
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/diagnostic-order-test-notes/by-diagnostic-orders/{orderId}")
    public ResponseEntity<List<DiagnosticOrderTestTechnicianNote>> listByOrderId(
            @PathVariable Long orderId,
            @ParameterObject Pageable pageable
    ) {
        LOG.debug("[TechnicianNote] LIST_BY_ORDER - request received. orderId={} pageable={}", orderId, pageable);
        Page<DiagnosticOrderTestTechnicianNote> page = noteRepository.findByOrderId(orderId, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticOrderTestTechnicianNote> body = page.getContent();

        LOG.debug("[TechnicianNote] LIST_BY_ORDER - response ready. orderId={} returned={} totalElements={} totalPages={}",
                orderId, page.getNumberOfElements(), page.getTotalElements(), page.getTotalPages());
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @DeleteMapping("/diagnostic-order-test-notes/{id}")
    public ResponseEntity<Void> delete(@Valid @PathVariable Long id) {
        LOG.debug("[TechnicianNote] DELETE - request received. id={}", id);
        DiagnosticOrderTestTechnicianNote existing = noteRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_test_technician_notes",
                        "Note not found with id " + id
                ));

        noteService.delete(existing.getId());
        LOG.debug("[TechnicianNote] DELETE - deleted successfully. id={}", id);
        return ResponseEntity.noContent().build();
    }
}
