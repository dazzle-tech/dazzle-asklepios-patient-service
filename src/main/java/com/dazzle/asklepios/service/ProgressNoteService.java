package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.ProgressNote;
import com.dazzle.asklepios.domain.ProgressNoteLog;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.ProgressNoteLogRepository;
import com.dazzle.asklepios.repository.ProgressNoteRepository;
import com.dazzle.asklepios.service.dto.progressNotes.ProgressNoteCreateDTO;
import com.dazzle.asklepios.service.dto.progressNotes.ProgressNoteUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class ProgressNoteService {

    private static final Logger LOG =
            LoggerFactory.getLogger(ProgressNoteService.class);

    private final ProgressNoteRepository repository;
    private final PatientRepository patientRepository;
    private final ProgressNoteLogRepository logRepository;


    public ProgressNoteService(
            ProgressNoteRepository repository,
            PatientRepository patientRepository,
            ProgressNoteLogRepository logRepository

    ) {
        this.repository = repository;
        this.patientRepository = patientRepository;
        this.logRepository = logRepository;
    }

    public ProgressNote create(ProgressNoteCreateDTO dto) {
        LOG.info("[CREATE] ProgressNote payload={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Patient not found",
                                "progressNote",
                                "patient.notfound"
                        )
                );

        ProgressNote entity = ProgressNote.builder()
                .patient(patient)
                .encounterId(dto.encounterId())
                .noteText(dto.noteText())
                .build();

        try {
            return repository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while saving progress note.",
                    "progressNote",
                    "db.constraint"
            );
        }
    }

    public ProgressNote update(Long id, ProgressNoteUpdateDTO dto) {
        ProgressNote existing = findById(id);
        existing.setNoteText(dto.noteText());

        try {
            return repository.saveAndFlush(existing);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating progress note.",
                    "progressNote",
                    "db.constraint"
            );
        }
    }

    public ProgressNote cancel(Long id, String reason, Long cancelledBy) {
        ProgressNote existing = findById(id);

        existing.setCancelledDate(Instant.now());
        existing.setCancelledBy(cancelledBy);
        existing.setCancellationReason(reason);

        try {
            return repository.saveAndFlush(existing);

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while cancelling progress note.",
                    "progressNote",
                    "db.constraint"
            );
        }
    }

    @Transactional(readOnly = true)
    public Page<ProgressNote> findByEncounterNotCancelled(
            Long encounterId,
            Pageable pageable
    ) {
        return repository.findByEncounterIdAndCancelledDateIsNull(
                encounterId,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public Page<ProgressNote> findByEncounterAll(
            Long encounterId,
            Pageable pageable
    ) {
        return repository.findByEncounterId(
                encounterId,
                pageable
        );
    }


    @Transactional(readOnly = true)
    public ProgressNote findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Progress note not found",
                                "progressNote",
                                "notfound"
                        )
                );
    }

    @Transactional(readOnly = true)
    public List<ProgressNoteLog> findLogsByProgressNoteId(Long progressNoteId) {
        findById(progressNoteId);

        return logRepository
                .findByProgressNoteIdOrderByCreatedDateDesc(progressNoteId);
    }

    private void handleConstraintsOnCreateOrUpdate(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        String lower = (message != null ? message.toLowerCase() : "");

        if (lower.contains("ck_progress_notes_cancellation_reason")
                || (lower.contains("check constraint") && lower.contains("cancellation_reason"))) {
            throw new BadRequestAlertException(
                    "cancellationReason is required when cancelling a progress note.",
                    "progressNote",
                    "cancellationReason.required"
            );
        }

        if (lower.contains("fk_progress_notes_patient")
                || (lower.contains("foreign key") && lower.contains("patient"))) {
            throw new BadRequestAlertException(
                    "Invalid patientId (patient not found).",
                    "progressNote",
                    "patient.invalid"
            );
        }

        if (lower.contains("note_text") && (lower.contains("null value") || lower.contains("not-null"))) {
            throw new BadRequestAlertException(
                    "noteText is required.",
                    "progressNote",
                    "noteText.required"
            );
        }

        if (lower.contains("encounter_id") && (lower.contains("null value") || lower.contains("not-null"))) {
            throw new BadRequestAlertException(
                    "encounterId is required.",
                    "progressNote",
                    "encounterId.required"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving progress note.",
                "progressNote",
                "db.constraint"
        );
    }
}
