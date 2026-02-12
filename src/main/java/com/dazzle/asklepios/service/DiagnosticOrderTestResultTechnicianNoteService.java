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

    private final DiagnosticOrderTestResultTechnicianNoteRepository noteRepository;
    private final DiagnosticOrderTestResultRepository testResultRepository;

    public DiagnosticOrderTestResultTechnicianNoteService(
            DiagnosticOrderTestResultTechnicianNoteRepository noteRepository,
            DiagnosticOrderTestResultRepository testResultRepository
    ) {
        this.noteRepository = noteRepository;
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
     * @param dto note create payload
     * @return persisted note
     * @throws BadRequestAlertException if result not found or orderTestId mismatch
     */
    public DiagnosticOrderTestResultTechnicianNote create(DiagnosticOrderTestResultTechnicianNoteDTO dto) {
        LOG.debug("[TestResultTechnicianNoteService] CREATE - start. payload={}", dto);

        DiagnosticOrderTestResult testResult = testResultRepository.findById(dto.resultId())
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

        DiagnosticOrderTestResultTechnicianNote note = new DiagnosticOrderTestResultTechnicianNote();
        note.setOrderTestId(dto.orderTestId());
        note.setResultId(dto.resultId());
        note.setNote(dto.note());

        DiagnosticOrderTestResultTechnicianNote saved = noteRepository.save(note);

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
        return noteRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_order_test_result_technician_notes",
                        "Note not found with id " + id
                ));
    }

    /**
     * Lists notes by orderTestId (no pagination).
     *
     * @param orderTestId diagnostic order test id
     * @return notes for the given orderTestId
     */
    @Transactional(readOnly = true)
    public List<DiagnosticOrderTestResultTechnicianNote> listByOrderTestId(Long orderTestId) {
        return noteRepository.findByOrderTestId(orderTestId);
    }

    /**
     * Lists notes by resultId (no pagination).
     *
     * @param resultId test result id
     * @return notes for the given resultId
     */
    @Transactional(readOnly = true)
    public List<DiagnosticOrderTestResultTechnicianNote> listByResultId(Long resultId) {
        return noteRepository.findByResultId(resultId);
    }

    /**
     * Deletes a note by id.
     *
     * @param id note id
     * @throws BadRequestAlertException if note not found
     */
    public void delete(Long id) {
        LOG.debug("[TestResultTechnicianNoteService] DELETE - start. id={}", id);

        if (!noteRepository.existsById(id)) {
            throw new BadRequestAlertException(
                    "notfound",
                    "diagnostic_order_test_result_technician_notes",
                    "Note not found with id " + id
            );
        }

        noteRepository.deleteById(id);

        LOG.debug("[TestResultTechnicianNoteService] DELETE - done. id={}", id);
    }
}
