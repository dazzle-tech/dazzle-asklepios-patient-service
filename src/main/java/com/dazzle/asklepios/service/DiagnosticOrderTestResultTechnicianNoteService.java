package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResultTechnicianNote;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultTechnicianNoteRepository;
import com.dazzle.asklepios.service.dto.laboratory.diagnosticordertestsresult.resulttechniciannote.DiagnosticOrderTestResultTechnicianNoteDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing {@link DiagnosticOrderTestResultTechnicianNote}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Create technician notes with basic invariants validation</li>
 *   <li>Read notes (by id, by orderTestId, by resultId)</li>
 *   <li>Delete notes</li>
 * </ul>
 * </p>
 */
@Service
@Transactional
public class DiagnosticOrderTestResultTechnicianNoteService {

    private static final Logger LOG =
            LoggerFactory.getLogger(DiagnosticOrderTestResultTechnicianNoteService.class);

    private final DiagnosticOrderTestResultTechnicianNoteRepository diagnosticOrderTestResultTechnicianNoteRepository;
    private final DiagnosticOrderTestResultRepository testResultRepository;

    public DiagnosticOrderTestResultTechnicianNoteService(
            DiagnosticOrderTestResultTechnicianNoteRepository noteRepository,
            DiagnosticOrderTestResultRepository testResultRepository
    ) {
        this.diagnosticOrderTestResultTechnicianNoteRepository = noteRepository;
        this.testResultRepository = testResultRepository;
    }

    /**
     * Creates a new technician note for a diagnostic order test result.
     *
     * <p>Validations:
     * <ul>
     *   <li>Result must exist</li>
     *   <li>DTO orderTestId must match the result's orderTestId</li>
     * </ul>
     * </p>
     *
     * @param orderTestResultTechnicianNoteDTO note create payload
     * @return persisted note
     * @throws BadRequestAlertException if result not found or orderTestId mismatch
     */
    public DiagnosticOrderTestResultTechnicianNote create(DiagnosticOrderTestResultTechnicianNoteDTO orderTestResultTechnicianNoteDTO) {
        LOG.debug("[TestResultTechnicianNoteService] CREATE - start. payload={}", orderTestResultTechnicianNoteDTO);

        DiagnosticOrderTestResult testResult = testResultRepository.findById(orderTestResultTechnicianNoteDTO.resultId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_test_results",
                        "DiagnosticOrderTestResult not found with id " + orderTestResultTechnicianNoteDTO.resultId()
                ));

        if (!testResult.getOrderTestId().equals(orderTestResultTechnicianNoteDTO.orderTestId())) {
            throw new BadRequestAlertException(
                    "order_test_mismatch",
                    "diagnostic_order_test_result_technician_notes",
                    "orderTestId does not match the given resultId"
            );
        }

        DiagnosticOrderTestResultTechnicianNote note = new DiagnosticOrderTestResultTechnicianNote();
        note.setOrderTestId(orderTestResultTechnicianNoteDTO.orderTestId());
        note.setResultId(orderTestResultTechnicianNoteDTO.resultId());
        note.setNote(orderTestResultTechnicianNoteDTO.note());

        DiagnosticOrderTestResultTechnicianNote saved = diagnosticOrderTestResultTechnicianNoteRepository.save(note);

        LOG.debug("[TestResultTechnicianNoteService] CREATE - done. id={} orderTestId={} resultId={}",
                saved.getId(), saved.getOrderTestId(), saved.getResultId());

        return saved;
    }

    /**
     * Loads a note by id.
     *
     * @param id note id
     * @return existing note
     * @throws BadRequestAlertException if note not found
     */
    @Transactional(readOnly = true)
    public DiagnosticOrderTestResultTechnicianNote getById(Long id) {
        LOG.debug("[TestResultTechnicianNoteService] GET_BY_ID - start. id={}", id);

        DiagnosticOrderTestResultTechnicianNote note = diagnosticOrderTestResultTechnicianNoteRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_test_result_technician_notes",
                        "Note not found with id " + id
                ));

        LOG.debug("[TestResultTechnicianNoteService] GET_BY_ID - done. id={} orderTestId={} resultId={}",
                note.getId(), note.getOrderTestId(), note.getResultId());
        return note;
    }

    /**
     * Lists notes by orderTestId (no pagination).
     *
     * @param orderTestId diagnostic order test id
     * @return notes for the given orderTestId
     */
    @Transactional(readOnly = true)
    public List<DiagnosticOrderTestResultTechnicianNote> listByOrderTestId(Long orderTestId) {
        LOG.debug("[TestResultTechnicianNoteService] LIST_BY_ORDER_TEST_ID - start. orderTestId={}", orderTestId);
        List<DiagnosticOrderTestResultTechnicianNote> notes =
                diagnosticOrderTestResultTechnicianNoteRepository.findByOrderTestId(orderTestId);
        LOG.debug("[TestResultTechnicianNoteService] LIST_BY_ORDER_TEST_ID - done. orderTestId={} returned={}",
                orderTestId, notes.size());
        return notes;
    }

    /**
     * Lists notes by resultId (no pagination).
     *
     * @param resultId test result id
     * @return notes for the given resultId
     */
    @Transactional(readOnly = true)
    public List<DiagnosticOrderTestResultTechnicianNote> listByResultId(Long resultId) {
        LOG.debug("[TestResultTechnicianNoteService] LIST_BY_RESULT_ID - start. resultId={}", resultId);
        List<DiagnosticOrderTestResultTechnicianNote> notes =
                diagnosticOrderTestResultTechnicianNoteRepository.findByResultId(resultId);
        LOG.debug("[TestResultTechnicianNoteService] LIST_BY_RESULT_ID - done. resultId={} returned={}",
                resultId, notes.size());
        return notes;
    }

    /**
     * Deletes a note by id.
     *
     * @param id note id
     * @throws BadRequestAlertException if note not found
     */
    public void delete(Long id) {
        LOG.debug("[TestResultTechnicianNoteService] DELETE - start. id={}", id);

        if (!diagnosticOrderTestResultTechnicianNoteRepository.existsById(id)) {
            throw new BadRequestAlertException(
                    "notfound",
                    "diagnostic_order_test_result_technician_notes",
                    "Note not found with id " + id
            );
        }

        diagnosticOrderTestResultTechnicianNoteRepository.deleteById(id);

        LOG.debug("[TestResultTechnicianNoteService] DELETE - done. id={}", id);
    }
}
