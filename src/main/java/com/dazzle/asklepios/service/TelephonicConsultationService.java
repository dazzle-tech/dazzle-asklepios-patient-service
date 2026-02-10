package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.TelephonicConsultation;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.TelephonicConsultationRepository;
import com.dazzle.asklepios.service.dto.telephonicconsultation.TelephonicConsultationCreateDTO;
import com.dazzle.asklepios.service.dto.telephonicconsultation.TelephonicConsultationUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class TelephonicConsultationService {

    private static final Logger LOG = LoggerFactory.getLogger(TelephonicConsultationService.class);

    private final TelephonicConsultationRepository repository;
    private final PatientRepository patientRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public TelephonicConsultationService(
            TelephonicConsultationRepository repository,
            PatientRepository patientRepository
    ) {
        this.repository = repository;
        this.patientRepository = patientRepository;
    }

    public TelephonicConsultation create(TelephonicConsultationCreateDTO dto) {
        LOG.info("[CREATE] TelephonicConsultation payload={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Patient not found with id " + dto.patientId(),
                                "telephonicConsultation",
                                "patient.notfound"
                        )
                );

        TelephonicConsultation entity = TelephonicConsultation.builder()
                .patient(patient)
                .encounterId(dto.encounterId())
                .practitionerId(dto.practitionerId())
                .dateOfCall(dto.dateOfCall())
                .consultationContent(dto.consultationContent())
                .approvalNumber(dto.approvalNumber())
                .notes(dto.notes())
                .extraDocumentation(dto.extraDocumentation())
                .status(DiagnosticStatus.NEW)
                .build();

        try {
            TelephonicConsultation saved = repository.saveAndFlush(entity);
            entityManager.refresh(saved);
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while creating telephonic consultation.",
                    "telephonicConsultation",
                    "db.constraint"
            );
        }
    }

    public TelephonicConsultation update(Long id, TelephonicConsultationUpdateDTO dto) {
        LOG.info("[UPDATE] TelephonicConsultation id={} payload={}", id, dto);

        TelephonicConsultation existing = repository.findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Telephonic consultation not found with id " + id,
                                "telephonicConsultation",
                                "notfound"
                        )
                );

        if (existing.getStatus() == DiagnosticStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled telephonic consultation cannot be updated",
                    "telephonicConsultation",
                    "already.cancelled"
            );
        }

        existing.setPractitionerId(dto.practitionerId());
        existing.setDateOfCall(dto.dateOfCall());
        existing.setConsultationContent(dto.consultationContent());
        existing.setApprovalNumber(dto.approvalNumber());
        existing.setNotes(dto.notes());
        existing.setExtraDocumentation(dto.extraDocumentation());


        try {
            TelephonicConsultation updated = repository.saveAndFlush(existing);
            entityManager.refresh(updated);
            return updated;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while updating telephonic consultation.",
                    "telephonicConsultation",
                    "db.constraint"
            );
        }
    }

    @Transactional(readOnly = true)
    public Page<TelephonicConsultation> findNotCancelled(
            Long encounterId,
            Pageable pageable
    ) {
        LOG.debug("[FIND_NOT_CANCELLED] encounterId={} pageable={}", encounterId, pageable);
        return repository.findByEncounterIdAndStatusNot(
                encounterId,
                DiagnosticStatus.CANCELLED,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public Page<TelephonicConsultation> findByEncounter(
            Long encounterId,
            Pageable pageable
    ) {
        LOG.debug("[FIND_BY_ENCOUNTER] encounterId={} pageable={}", encounterId, pageable);

        Page<TelephonicConsultation> page = repository.findByEncounterId(encounterId, pageable);

        LOG.debug("[FIND_BY_ENCOUNTER_RESULT] encounterId={} pageNumber={} pageSize={} totalElements={} totalPages={} returned={}",
                encounterId,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumberOfElements()
        );

        return page;
    }

    @Transactional(readOnly = true)
    public Page<TelephonicConsultation> findByEncounterFromDate(
            Long encounterId,
            Instant fromDate,
            Pageable pageable
    ) {
        LOG.debug("[FIND_BY_ENCOUNTER_FROM_DATE] encounterId={} fromDate={} pageable={}",
                encounterId, fromDate, pageable);

        return repository.findByEncounterIdAndCreatedDateAfter(
                encounterId,
                fromDate,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public Page<TelephonicConsultation> findByEncounterToDate(
            Long encounterId,
            Instant toDate,
            Pageable pageable
    ) {
        LOG.debug("[FIND_BY_ENCOUNTER_TO_DATE] encounterId={} toDate={} pageable={}",
                encounterId, toDate, pageable);

        return repository.findByEncounterIdAndCreatedDateBefore(
                encounterId,
                toDate,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public Page<TelephonicConsultation> findByEncounterFromDateNotCancelled(
            Long encounterId,
            Instant fromDate,
            Pageable pageable
    ) {
        return repository.findByEncounterIdAndCreatedDateAfterAndStatusNot(
                encounterId,
                fromDate,
                DiagnosticStatus.CANCELLED,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public Page<TelephonicConsultation> findByEncounterToDateNotCancelled(
            Long encounterId,
            Instant toDate,
            Pageable pageable
    ) {
        return repository.findByEncounterIdAndCreatedDateBeforeAndStatusNot(
                encounterId,
                toDate,
                DiagnosticStatus.CANCELLED,
                pageable
        );
    }


    @Transactional(readOnly = true)
    public Page<TelephonicConsultation> findByEncounterNotCancelled(
            Long encounterId,
            Pageable pageable
    ) {
        LOG.debug("[FIND_BY_ENCOUNTER_NOT_CANCELLED] encounterId={} statusNot={} pageable={}",
                encounterId, DiagnosticStatus.CANCELLED, pageable);

        Page<TelephonicConsultation> page = repository.findByEncounterIdAndStatusNot(
                encounterId,
                DiagnosticStatus.CANCELLED,
                pageable
        );

        LOG.debug("[FIND_BY_ENCOUNTER_NOT_CANCELLED_RESULT] encounterId={} statusNot={} pageNumber={} pageSize={} totalElements={} totalPages={} returned={}",
                encounterId,
                DiagnosticStatus.CANCELLED,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumberOfElements()
        );

        return page;
    }

    @Transactional(readOnly = true)
    public Page<TelephonicConsultation> findByEncounterWithDateRange(
            Long encounterId,
            Instant fromDate,
            Instant toDate,
            Pageable pageable
    ) {
        LOG.debug("[FIND_BY_ENCOUNTER_DATE_RANGE] encounterId={} fromDate={} toDate={} pageable={}",
                encounterId, fromDate, toDate, pageable);

        Page<TelephonicConsultation> page = repository.findByEncounterIdAndCreatedDateBetween(
                encounterId,
                fromDate,
                toDate,
                pageable
        );

        LOG.debug("[FIND_BY_ENCOUNTER_DATE_RANGE_RESULT] encounterId={} fromDate={} toDate={} pageNumber={} pageSize={} totalElements={} totalPages={} returned={}",
                encounterId,
                fromDate,
                toDate,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumberOfElements()
        );

        return page;
    }

    @Transactional(readOnly = true)
    public Page<TelephonicConsultation> findByEncounterWithDateRangeNotCancelled(
            Long encounterId,
            Instant fromDate,
            Instant toDate,
            Pageable pageable
    ) {
        LOG.debug("[FIND_BY_ENCOUNTER_DATE_RANGE_NOT_CANCELLED] encounterId={} fromDate={} toDate={} statusNot={} pageable={}",
                encounterId, fromDate, toDate, DiagnosticStatus.CANCELLED, pageable);

        Page<TelephonicConsultation> page = repository.findByEncounterIdAndCreatedDateBetweenAndStatusNot(
                encounterId,
                fromDate,
                toDate,
                DiagnosticStatus.CANCELLED,
                pageable
        );

        LOG.debug("[FIND_BY_ENCOUNTER_DATE_RANGE_NOT_CANCELLED_RESULT] encounterId={} fromDate={} toDate={} statusNot={} pageNumber={} pageSize={} totalElements={} totalPages={} returned={}",
                encounterId,
                fromDate,
                toDate,
                DiagnosticStatus.CANCELLED,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumberOfElements()
        );

        return page;
    }


    public TelephonicConsultation cancel(
            Long id,
            String cancellationReason,
            Long cancelledByUserId
    ) {
        LOG.info("[CANCEL] TelephonicConsultation id={} reason={}", id, cancellationReason);

        TelephonicConsultation existing = repository.findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Telephonic consultation not found with id " + id,
                                "telephonicConsultation",
                                "notfound"
                        )
                );

        if (existing.getStatus() == DiagnosticStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Telephonic consultation already cancelled",
                    "telephonicConsultation",
                    "already.cancelled"
            );
        }

        existing.setStatus(DiagnosticStatus.CANCELLED);
        existing.setCancellationReason(cancellationReason);
        existing.setCancelledAt(Instant.now());
        existing.setCancelledBy(cancelledByUserId);


        try {
            TelephonicConsultation cancelled = repository.saveAndFlush(existing);
            entityManager.refresh(cancelled);
            return cancelled;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            handleConstraintsOnCreateOrUpdate(ex);
            throw new BadRequestAlertException(
                    "Database constraint violated while cancelling telephonic consultation.",
                    "telephonicConsultation",
                    "db.constraint"
            );
        }
    }


    private void handleConstraintsOnCreateOrUpdate(RuntimeException exception) {
        Throwable root = getRootCause(exception);
        String message = (root != null ? root.getMessage() : exception.getMessage());

        LOG.error("DB ROOT CAUSE: {}", message, exception);

        String lower = (message != null ? message.toLowerCase() : "");

        if (lower.contains("patient") && lower.contains("foreign key")) {
            throw new BadRequestAlertException(
                    "Invalid patient reference.",
                    "telephonicConsultation",
                    "fk.patient"
            );
        }

        if (lower.contains("encounter") && lower.contains("not-null")) {
            throw new BadRequestAlertException(
                    "Encounter id is required.",
                    "telephonicConsultation",
                    "encounter.required"
            );
        }

        if (lower.contains("approval") && lower.contains("unique")) {
            throw new BadRequestAlertException(
                    "Approval number already exists.",
                    "telephonicConsultation",
                    "unique.approval_number"
            );
        }

        if (lower.contains("date_of_call") && lower.contains("not-null")) {
            throw new BadRequestAlertException(
                    "Date of call is required.",
                    "telephonicConsultation",
                    "date.required"
            );
        }

        throw new BadRequestAlertException(
                "Database constraint violated while saving telephonic consultation.",
                "telephonicConsultation",
                "db.constraint"
        );
    }
}
