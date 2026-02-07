package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureCreateDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientProcedureService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientProcedureService.class);

    private final PatientProcedureRepository repository;
    private final PatientRepository patientRepository;

    public PatientProcedure create(PatientProcedureCreateDTO dto) {
        LOG.info("[CREATE] PatientProcedure payload={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> {
                    LOG.warn("[CREATE] PatientProcedure rejected: patient not found patientId={}", dto.patientId());
                    return new NotFoundAlertException(
                            "Patient not found with id " + dto.patientId(),
                            "procedure",
                            "patient.notfound"
                    );
                });

        PatientProcedure entity = PatientProcedure.builder()
                .procedureId(dto.procedureId())
                .patient(patient)
                .encounterId(dto.encounterId())
                .fromFacilityId(dto.fromFacilityId())
                .toFacilityId(dto.toFacilityId())
                .fromDepartmentId(dto.fromDepartmentId())
                .toDepartmentId(dto.toDepartmentId())
                .indicationId(dto.indicationId())
                .procedureLevel(dto.procedureLevel())
                .priority(dto.priority())
                .bodyPart(dto.bodyPart())
                .side(dto.side())
                .scheduledDateTime(dto.scheduledDateTime())
                .notes(dto.notes())
                .extraDocumentation(dto.extraDocumentation())
                .status(ProcStatus.REQUESTED)
                .build();

        try {
            PatientProcedure saved = repository.saveAndFlush(entity);
            LOG.info("[CREATE] PatientProcedure success id={}", saved.getId());
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CREATE] PatientProcedure failed (constraint) payload={}", dto, ex);
            throw handleConstraintViolation(ex);
        }
    }

    public PatientProcedure update(Long id, PatientProcedureUpdateDTO dto) {
        LOG.info("[UPDATE] PatientProcedure id={} payload={}", id, dto);

        PatientProcedure entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Procedure not found with id " + id,
                        "procedure",
                        "notfound"
                ));


        entity.setSide(dto.side());
        entity.setIndicationId(dto.indicationId());
        entity.setToFacilityId(dto.toFacilityId());
        entity.setToDepartmentId(dto.toDepartmentId());
        entity.setScheduledDateTime(dto.scheduledDateTime());
        entity.setNotes(dto.notes());
        entity.setExtraDocumentation(dto.extraDocumentation());

        try {
            PatientProcedure saved = repository.saveAndFlush(entity);
            LOG.info("[UPDATE] PatientProcedure success id={}", saved.getId());
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[UPDATE] PatientProcedure failed (constraint) id={} payload={}", id, dto, ex);
            throw handleConstraintViolation(ex);
        }
    }

    public PatientProcedure cancel(Long id, String reason, Long cancelledBy) {
        LOG.info("[CANCEL] PatientProcedure id={} cancelledBy={} reason={}", id, cancelledBy, reason);

        PatientProcedure entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Procedure not found with id " + id,
                        "procedure",
                        "notfound"
                ));

        entity.setStatus(ProcStatus.CANCELLED);
        entity.setCancelledDate(Instant.now());
        entity.setCancelledBy(cancelledBy);
        entity.setCancellationReason(reason);

        try {
            PatientProcedure saved = repository.saveAndFlush(entity);
            LOG.info("[CANCEL] PatientProcedure success id={}", saved.getId());
            return saved;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CANCEL] PatientProcedure failed (constraint) id={}", id, ex);
            throw handleConstraintViolation(ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<PatientProcedure> findByEncounter(Long encounterId, boolean includeCancelled, Pageable pageable) {
        return includeCancelled
                ? repository.findByEncounterId(encounterId, pageable)
                : repository.findByEncounterIdAndStatusNot(encounterId, ProcStatus.CANCELLED, pageable);
    }

    @Transactional(readOnly = true)
    public PatientProcedure findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Procedure not found",
                        "procedure",
                        "notfound"
                ));
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String msg = root != null ? root.getMessage() : exception.getMessage();
        String m = msg != null ? msg.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] PatientProcedure violated rootMessage={}", msg, exception);

        if (m.contains("uk_procedure_unique_context_active")) {
            return new BadRequestAlertException(
                    "This procedure already exists for the same encounter and body part.",
                    "procedure",
                    "duplicate"
            );
        }

        if (m.contains("fk_procedure_patient")) {
            return new BadRequestAlertException("Invalid patient id.", "procedure", "patient.invalid");
        }
        if (m.contains("fk_procedure_from_facility")) {
            return new BadRequestAlertException("Invalid from facility id.", "procedure", "fromFacility.invalid");
        }
        if (m.contains("fk_procedure_to_facility")) {
            return new BadRequestAlertException("Invalid to facility id.", "procedure", "toFacility.invalid");
        }
        if (m.contains("fk_procedure_from_department")) {
            return new BadRequestAlertException("Invalid from department id.", "procedure", "fromDepartment.invalid");
        }
        if (m.contains("fk_procedure_to_department")) {
            return new BadRequestAlertException("Invalid to department id.", "procedure", "toDepartment.invalid");
        }
        if (m.contains("fk_procedure_indication_icd")) {
            return new BadRequestAlertException("Invalid indication (ICD) id.", "procedure", "indication.invalid");
        }
        if (m.contains("fk_procedure_cancelled_by")) {
            return new BadRequestAlertException("Invalid cancelledBy user.", "procedure", "cancelledBy.invalid");
        }

        if (m.contains("ck_procedure_level_enum")) {
            return new BadRequestAlertException("Invalid procedure level.", "procedure", "procedureLevel.invalid");
        }
        if (m.contains("ck_procedure_status_enum")) {
            return new BadRequestAlertException("Invalid procedure status.", "procedure", "status.invalid");
        }
        if (m.contains("ck_procedure_cancellation_reason")) {
            return new BadRequestAlertException(
                    "Cancellation reason is required when cancelling a procedure.",
                    "procedure",
                    "cancellationReason.required"
            );
        }
        if (m.contains("scheduled_date_time") && m.contains("not-null")) {
            return new BadRequestAlertException(
                    "Scheduled date time is required.",
                    "procedure",
                    "schedule.required"
            );
        }
        if (m.contains("body_part") && m.contains("not-null")) {
            return new BadRequestAlertException(
                    "Body part is required.",
                    "procedure",
                    "bodyPart.required"
            );
        }


        return new BadRequestAlertException(
                "Database constraint violated while saving procedure.",
                "procedure",
                "db.constraint"
        );
    }
}
