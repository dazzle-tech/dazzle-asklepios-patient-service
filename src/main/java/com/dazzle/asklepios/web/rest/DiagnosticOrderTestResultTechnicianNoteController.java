package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticOrderTestResultTechnicianNote;
import com.dazzle.asklepios.service.DiagnosticOrderTestResultTechnicianNoteService;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.resulttechniciannote.DiagnosticOrderTestResultTechnicianNoteDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * REST controller for managing {@link DiagnosticOrderTestResultTechnicianNote}.
 *
 * <p>Exposes endpoints to:
 * <ul>
 *   <li>Create a technician note for a test result</li>
 *   <li>Fetch a note by id</li>
 *   <li>List notes by orderTestId</li>
 *   <li>List notes by resultId</li>
 *   <li>Delete a note</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/patient")
public class DiagnosticOrderTestResultTechnicianNoteController {

    private static final Logger LOG =
            LoggerFactory.getLogger(DiagnosticOrderTestResultTechnicianNoteController.class);

    private final DiagnosticOrderTestResultTechnicianNoteService orderTestResultTechnicianNoteService;

    public DiagnosticOrderTestResultTechnicianNoteController(
            DiagnosticOrderTestResultTechnicianNoteService noteService
    ) {
        this.orderTestResultTechnicianNoteService = noteService;
    }

    /**
     * Creates a new technician note for a test result.
     *
     * @param dto create payload
     * @return created note (HTTP 201)
     */
    @PostMapping("/diagnostic-order-test-result-notes")
    public ResponseEntity<DiagnosticOrderTestResultTechnicianNote> create(
            @Valid @RequestBody DiagnosticOrderTestResultTechnicianNoteDTO dto
    ) {
        LOG.debug("[DiagnosticOrderTestResultTechnicianNote] CREATE - request received. payload={}", dto);
        DiagnosticOrderTestResultTechnicianNote saved = orderTestResultTechnicianNoteService.create(dto);
        LOG.debug("[DiagnosticOrderTestResultTechnicianNote] CREATE - created successfully. id={} resultId={} orderTestId={}",
                saved.getId(), saved.getResultId(), saved.getOrderTestId());

        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-order-test-result-notes/" + saved.getId()))
                .body(saved);
    }

    /**
     * Returns a technician note by id.
     *
     * @param id note id
     * @return note (HTTP 200)
     */
    @GetMapping("/diagnostic-order-test-result-notes/{id}")
    public ResponseEntity<DiagnosticOrderTestResultTechnicianNote> getById(@PathVariable Long id) {
        LOG.debug("[DiagnosticOrderTestResultTechnicianNote] GET_BY_ID - request received. id={}", id);
        DiagnosticOrderTestResultTechnicianNote note = orderTestResultTechnicianNoteService.getById(id);
        LOG.debug("[DiagnosticOrderTestResultTechnicianNote] GET_BY_ID - found. id={} resultId={} orderTestId={}",
                note.getId(), note.getResultId(), note.getOrderTestId());
        return ResponseEntity.ok(note);
    }

    /**
     * Lists technician notes by orderTestId (no pagination).
     *
     * @param orderTestId diagnostic order test id
     * @return list of notes (HTTP 200)
     */
    @GetMapping("/diagnostic-order-tests/{orderTestId}/result-notes")
    public ResponseEntity<List<DiagnosticOrderTestResultTechnicianNote>> listByOrderTestId(
            @PathVariable Long orderTestId
    ) {
        LOG.debug("[DiagnosticOrderTestResultTechnicianNote] LIST_BY_ORDER_TEST_ID - request received. orderTestId={}", orderTestId);
        List<DiagnosticOrderTestResultTechnicianNote> body = orderTestResultTechnicianNoteService.listByOrderTestId(orderTestId);
        LOG.debug("[DiagnosticOrderTestResultTechnicianNote] LIST_BY_ORDER_TEST_ID - response ready. orderTestId={} returned={}",
                orderTestId, body.size());

        return ResponseEntity.ok(body);
    }

    /**
     * Lists technician notes by resultId (no pagination).
     *
     * @param resultId test result id
     * @return list of notes (HTTP 200)
     */
    @GetMapping("/diagnostic-order-test-results/{resultId}/notes")
    public ResponseEntity<List<DiagnosticOrderTestResultTechnicianNote>> listByResultId(
            @PathVariable Long resultId
    ) {
        LOG.debug("[DiagnosticOrderTestResultTechnicianNote] LIST_BY_RESULT_ID - request received. resultId={}", resultId);
        List<DiagnosticOrderTestResultTechnicianNote> body = orderTestResultTechnicianNoteService.listByResultId(resultId);
        LOG.debug("[DiagnosticOrderTestResultTechnicianNote] LIST_BY_RESULT_ID - response ready. resultId={} returned={}",
                resultId, body.size());

        return ResponseEntity.ok(body);
    }

    /**
     * Deletes a technician note by id.
     *
     * @param id note id
     * @return HTTP 204 on success
     */
    @DeleteMapping("/diagnostic-order-test-result-notes/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        LOG.debug("[DiagnosticOrderTestResultTechnicianNote] DELETE - request received. id={}", id);
        orderTestResultTechnicianNoteService.delete(id);
        LOG.debug("[DiagnosticOrderTestResultTechnicianNote] DELETE - deleted successfully. id={}", id);
        return ResponseEntity.noContent().build();
    }
}
