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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;

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

        if (dto == null) {
            throw new BadRequestAlertException(
                    "Telephonic consultation payload is required",
                    "telephonicConsultation",
                    "payload.required"
            );
        }

        if (dto.patientId() == null) {
            throw new BadRequestAlertException(
                    "Patient id is required",
                    "telephonicConsultation",
                    "patient.required"
            );
        }

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "telephonicConsultation",
                        "patient.notfound"
                ));

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

        } catch (Exception ex) {
            LOG.error("Failed to create telephonic consultation", ex);
            throw new BadRequestAlertException(
                    "Failed to create telephonic consultation",
                    "telephonicConsultation",
                    "create.failed"
            );
        }
    }


    public TelephonicConsultation update(Long id, TelephonicConsultationUpdateDTO dto) {
        LOG.info("[UPDATE] TelephonicConsultation id={} payload={}", id, dto);

        if (id == null) {
            throw new BadRequestAlertException(
                    "Telephonic consultation id is required",
                    "telephonicConsultation",
                    "id.required"
            );
        }

        if (dto == null) {
            throw new BadRequestAlertException(
                    "Telephonic consultation payload is required",
                    "telephonicConsultation",
                    "payload.required"
            );
        }

        TelephonicConsultation existing = repository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Telephonic consultation not found with id " + id,
                        "telephonicConsultation",
                        "notfound"
                ));

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
        existing.setLastModifiedDate(Instant.now());

        try {
            TelephonicConsultation updated = repository.saveAndFlush(existing);
            entityManager.refresh(updated);
            return updated;

        } catch (Exception ex) {
            LOG.error("Failed to update telephonic consultation id={}", id, ex);
            throw new BadRequestAlertException(
                    "Failed to update telephonic consultation",
                    "telephonicConsultation",
                    "update.failed"
            );
        }
    }


    @Transactional(readOnly = true)
    public Page<TelephonicConsultation> findCancelled(Long encounterId, Pageable pageable) {
        LOG.debug("[FIND_CANCELLED] encounterId={} pageable={}", encounterId, pageable);

        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "Encounter id is required",
                    "telephonicConsultation",
                    "encounter.required"
            );
        }

        return repository.findByEncounterIdAndStatus(
                encounterId,
                DiagnosticStatus.CANCELLED,
                pageable
        );
    }


    @Transactional(readOnly = true)
    public Page<TelephonicConsultation> findNotCancelled(Long encounterId, Pageable pageable) {
        LOG.debug("[FIND_NOT_CANCELLED] encounterId={} pageable={}", encounterId, pageable);

        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "Encounter id is required",
                    "telephonicConsultation",
                    "encounter.required"
            );
        }

        return repository.findByEncounterIdAndStatusNot(
                encounterId,
                DiagnosticStatus.CANCELLED,
                pageable
        );
    }


    public TelephonicConsultation cancel(Long id, String cancellationReason, Long cancelledByUserId) {
        LOG.info("[CANCEL] TelephonicConsultation id={} reason={}", id, cancellationReason);

        if (id == null) {
            throw new BadRequestAlertException(
                    "Telephonic consultation id is required",
                    "telephonicConsultation",
                    "id.required"
            );
        }

        TelephonicConsultation existing = repository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Telephonic consultation not found with id " + id,
                        "telephonicConsultation",
                        "notfound"
                ));

        if (existing.getStatus() == DiagnosticStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Telephonic consultation already cancelled",
                    "telephonicConsultation",
                    "already.cancelled"
            );
        }

        existing.setStatus(DiagnosticStatus.CANCELLED);
        existing.setCancellationReason(cancellationReason);
        existing.setCancelledAt(new Date());
        existing.setCancelledBy(cancelledByUserId);
        existing.setLastModifiedDate(Instant.now());

        try {
            TelephonicConsultation cancelled = repository.saveAndFlush(existing);
            entityManager.refresh(cancelled);
            return cancelled;

        } catch (Exception ex) {
            LOG.error("Failed to cancel telephonic consultation id={}", id, ex);
            throw new BadRequestAlertException(
                    "Failed to cancel telephonic consultation",
                    "telephonicConsultation",
                    "cancel.failed"
            );
        }
    }
}
