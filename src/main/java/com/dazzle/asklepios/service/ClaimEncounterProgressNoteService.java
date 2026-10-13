package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimEncounterProgressNote;
import com.dazzle.asklepios.repository.ClaimEncounterCopyRepository;
import com.dazzle.asklepios.repository.ClaimEncounterProgressNoteRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.progressNotes.ClaimEncounterProgressNoteUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class ClaimEncounterProgressNoteService {

    private static final Logger LOG =
            LoggerFactory.getLogger(ClaimEncounterProgressNoteService.class);

    private final ClaimEncounterProgressNoteRepository repository;
    private final ClaimEncounterCopyRepository claimEncounterCopyRepository;

    public ClaimEncounterProgressNoteService(
            ClaimEncounterProgressNoteRepository repository,
            ClaimEncounterCopyRepository claimEncounterCopyRepository
    ) {
        this.repository = repository;
        this.claimEncounterCopyRepository = claimEncounterCopyRepository;
    }

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);

        if (username == null) {
            LOG.warn(
                    "[ClaimEncounterProgressNoteService] AUTH - unauthenticated request"
            );

            throw new BadRequestAlertException(
                    "unauthenticated",
                    "claimEncounterProgressNote",
                    "No authenticated user"
            );
        }

        return username;
    }

    public ClaimEncounterProgressNote update(
            Long id,
            ClaimEncounterProgressNoteUpdateDTO dto
    ) {
        ClaimEncounterProgressNote existing = findById(id);

        if (existing.getCancelledDate() != null) {
            throw new BadRequestAlertException(
                    "Cancelled progress note cannot be updated",
                    "claimEncounterProgressNote",
                    "already.cancelled"
            );
        }

        existing.setNoteText(dto.noteText());

        try {
            return repository.saveAndFlush(existing);
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);

            throw new BadRequestAlertException(
                    "Database constraint violated while updating progress note.",
                    "claimEncounterProgressNote",
                    "db.constraint"
            );
        }
    }

    public ClaimEncounterProgressNote cancel(
            Long id,
            String reason
    ) {
        ClaimEncounterProgressNote existing = findById(id);

        if (existing.getCancelledDate() != null) {
            throw new BadRequestAlertException(
                    "Progress note already cancelled",
                    "claimEncounterProgressNote",
                    "already.cancelled"
            );
        }

        existing.setCancelledDate(Instant.now());
        existing.setCancelledBy(currentUsername());
        existing.setCancellationReason(reason);

        LOG.debug(
                "[CANCEL] ClaimEncounterProgressNote id={} cancelledBy={}",
                id,
                existing.getCancelledBy()
        );

        try {
            return repository.saveAndFlush(existing);
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);

            throw new BadRequestAlertException(
                    "Database constraint violated while cancelling progress note.",
                    "claimEncounterProgressNote",
                    "db.constraint"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<ClaimEncounterProgressNote> findByClaimEncounterCopyIdNotCancelled(
            Long claimEncounterCopyId
    ) {
        return repository.findByClaimEncounterCopyIdAndCancelledDateIsNull(
                claimEncounterCopyId
        );
    }

    @Transactional(readOnly = true)
    public List<ClaimEncounterProgressNote> findByClaimEncounterCopyId(
            Long claimEncounterCopyId
    ) {
        return repository.findByClaimEncounterCopyId(claimEncounterCopyId);
    }

    @Transactional(readOnly = true)
    public ClaimEncounterProgressNote findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Progress note not found",
                                "claimEncounterProgressNote",
                                "notfound"
                        )
                );
    }

    private void handleConstraintsOnCreateOrUpdate(
            RuntimeException exception
    ) {
        Throwable root = getRootCause(exception);

        String message =
                root != null
                        ? root.getMessage()
                        : exception.getMessage();

        LOG.error(
                "DB ROOT CAUSE: {}",
                message,
                exception
        );

        String lower =
                message != null
                        ? message.toLowerCase()
                        : "";

        if (lower.contains(
                "ck_claim_encounter_progress_notes_cancellation_reason"
        ) || (
                lower.contains("check constraint")
                        && lower.contains("cancellation_reason")
        )) {
            throw new BadRequestAlertException(
                    "cancellationReason is required when cancelling a progress note.",
                    "claimEncounterProgressNote",
                    "cancellationReason.required"
            );
        }

        if (lower.contains("note_text")
                && (lower.contains("null value")
                || lower.contains("not-null"))) {
            throw new BadRequestAlertException(
                    "noteText is required.",
                    "claimEncounterProgressNote",
                    "noteText.required"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving progress note.",
                "claimEncounterProgressNote",
                "db.constraint"
        );
    }
}