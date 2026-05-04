package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.ProcedureClient;
import com.dazzle.asklepios.client.setup.dto.ProcedureSetupDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.domain.enumeration.ProcedureLevel;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureCreateDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureUpdateDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientProcedureService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientProcedureService.class);

    private final PatientProcedureRepository procedureRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final ProcedureClient procedureClient;
    private final FacilityHelper facilityHelper;
    private final DepartmentHelper departmentHelper;

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (username == null) {
            LOG.warn("[PatientProcedureService] AUTH - unauthenticated request");
            throw new BadRequestAlertException(
                    "unauthenticated",
                    "procedure",
                    "No authenticated user"
            );
        }
        return username;
    }

    public PatientProcedure create(PatientProcedureCreateDTO procedureCreateDTO) {
        LOG.info("[CREATE] PatientProcedure payload={}", procedureCreateDTO);

        Patient patient = patientRepository.findById(procedureCreateDTO.patientId())
                .orElseThrow(() -> {
                    LOG.warn(
                            "[CREATE] PatientProcedure rejected: patient not found patientId={}",
                            procedureCreateDTO.patientId()
                    );
                    return new NotFoundAlertException(
                            "Patient not found with id " + procedureCreateDTO.patientId(),
                            "procedure",
                            "patient.notfound"
                    );
                });

        PatientEncounter encounter = patientEncounterRepository.findById(procedureCreateDTO.encounterId())
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Encounter not found with id " + procedureCreateDTO.encounterId(),
                                "procedure",
                                "encounter.notfound"
                        )
                );

        ProcedureSetupDTO setupProcedure = fetchProcedureSetup(procedureCreateDTO.procedureId());
        facilityHelper.validateFacilityExists(procedureCreateDTO.fromFacilityId());
        facilityHelper.validateFacilityExists(procedureCreateDTO.toFacilityId());
        departmentHelper.validateDepartmentExists(procedureCreateDTO.fromDepartmentId());
        departmentHelper.validateDepartmentExists(procedureCreateDTO.toDepartmentId());

        PatientProcedure procedureEntity = PatientProcedure.builder()
                .procedureId(procedureCreateDTO.procedureId())
                .patient(patient)
                .encounter(encounter)
                .fromFacilityId(procedureCreateDTO.fromFacilityId())
                .toFacilityId(procedureCreateDTO.toFacilityId())
                .fromDepartmentId(procedureCreateDTO.fromDepartmentId())
                .toDepartmentId(procedureCreateDTO.toDepartmentId())
                .indicationId(procedureCreateDTO.indicationId())
                .procedureLevel(procedureCreateDTO.procedureLevel())
                .priority(procedureCreateDTO.priority())
                .bodyPart(procedureCreateDTO.bodyPart())
                .side(procedureCreateDTO.side())
                .scheduledDateTime(procedureCreateDTO.scheduledDateTime())
                .notes(procedureCreateDTO.notes())
                .extraDocumentation(procedureCreateDTO.extraDocumentation())
                .status(ProcStatus.REQUESTED)
                .build();

        try {
            PatientProcedure savedProcedure = procedureRepository.saveAndFlush(procedureEntity);

            PatientServiceAndProduct billingItem = buildProcedureBillingItem(
                    patient.getId(),
                    encounter.getId(),
                    savedProcedure.getId(),
                    setupProcedure,
                    procedureCreateDTO.notes()
            );

            patientServiceAndProductRepository.saveAndFlush(billingItem);

            LOG.info(
                    "[CREATE] PatientProcedure success id={} with billing item created",
                    savedProcedure.getId()
            );
            return savedProcedure;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CREATE] PatientProcedure failed (constraint) payload={}", procedureCreateDTO, ex);
            throw handleConstraintViolation(ex);
        }
    }

    public PatientProcedure update(Long id, PatientProcedureUpdateDTO procedureUpdateDTO) {
        LOG.info("[UPDATE] PatientProcedure id={} payload={}", id, procedureUpdateDTO);

        PatientProcedure procedureEntity = procedureRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Procedure not found with id " + id,
                        "procedure",
                        "notfound"
                ));
        facilityHelper.validateFacilityExists(procedureUpdateDTO.toFacilityId());
        departmentHelper.validateDepartmentExists(procedureUpdateDTO.toDepartmentId());

        procedureEntity.setProcedureId(procedureUpdateDTO.procedureId());
        procedureEntity.setProcedureLevel(
                procedureUpdateDTO.procedureLevel() != null
                        ? ProcedureLevel.valueOf(procedureUpdateDTO.procedureLevel())
                        : null
        );
        procedureEntity.setPriority(procedureUpdateDTO.priority());
        procedureEntity.setBodyPart(procedureUpdateDTO.bodyPart());
        procedureEntity.setSide(procedureUpdateDTO.side());
        procedureEntity.setIndicationId(procedureUpdateDTO.indicationId());
        procedureEntity.setToFacilityId(procedureUpdateDTO.toFacilityId());
        procedureEntity.setToDepartmentId(procedureUpdateDTO.toDepartmentId());
        procedureEntity.setScheduledDateTime(procedureUpdateDTO.scheduledDateTime());
        procedureEntity.setNotes(procedureUpdateDTO.notes());
        procedureEntity.setExtraDocumentation(procedureUpdateDTO.extraDocumentation());

        try {
            PatientProcedure saved = procedureRepository.saveAndFlush(procedureEntity);
            LOG.info("[UPDATE] PatientProcedure success id={}", saved.getId());
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[UPDATE] PatientProcedure failed (constraint) id={} payload={}", id, procedureUpdateDTO, ex);
            throw handleConstraintViolation(ex);
        }
    }

    public PatientProcedure cancel(Long id, String reason) {
        LOG.info("[CANCEL] PatientProcedure id={} reason={}", id, reason);

        PatientProcedure procedureEntity = procedureRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Procedure not found with id " + id,
                        "procedure",
                        "notfound"
                ));

        procedureEntity.setStatus(ProcStatus.CANCELLED);
        procedureEntity.setCancelledDate(Instant.now());
        procedureEntity.setCancelledBy(currentUsername());
        procedureEntity.setCancellationReason(reason);

        LOG.debug("[CANCEL] PatientProcedure cancelledBy={}", procedureEntity.getCancelledBy());

        try {
            PatientProcedure saved = procedureRepository.saveAndFlush(procedureEntity);
            LOG.info("[CANCEL] PatientProcedure success id={}", saved.getId());
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[CANCEL] PatientProcedure failed (constraint) id={}", id, ex);
            throw handleConstraintViolation(ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<PatientProcedure> findByEncounter(
            Long encounterId,
            boolean includeCancelled,
            Pageable pageable
    ) {
        return includeCancelled
                ? procedureRepository.findByEncounterId(encounterId, pageable)
                : procedureRepository.findByEncounterIdAndStatusNot(
                encounterId,
                ProcStatus.CANCELLED,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public Page<PatientProcedure> findByPatient(
            Long patientId,
            boolean includeCancelled,
            Pageable pageable
    ) {
        return includeCancelled
                ? procedureRepository.findByPatientId(patientId, pageable)
                : procedureRepository.findByPatientIdAndStatusNot(
                patientId,
                ProcStatus.CANCELLED,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public PatientProcedure findById(Long id) {
        LOG.info("[GET] PatientProcedure id={}", id);
        PatientProcedure procedure = procedureRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.warn("[GET] PatientProcedure not found id={}", id);
                    return new NotFoundAlertException(
                            "Procedure not found",
                            "procedure",
                            "notfound"
                    );
                });
        LOG.info("[GET] PatientProcedure found id={}", procedure.getId());
        return procedure;
    }

    private ProcedureSetupDTO fetchProcedureSetup(Long procedureId) {
        try {
            ProcedureSetupDTO response = procedureClient.getProcedure(procedureId);

            if (response == null || response.id() == null) {
                throw new NotFoundAlertException(
                        "Procedure setup not found with id " + procedureId,
                        "procedure",
                        "procedureSetup.notfound"
                );
            }

            return response;

        } catch (FeignException.NotFound ex) {
            LOG.error(
                    "[SETUP_SERVICE] Procedure not found. id={} status={} body={}",
                    procedureId,
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );

            throw new NotFoundAlertException(
                    "Procedure setup not found with id " + procedureId,
                    "procedure",
                    "procedureSetup.notfound"
            );

        } catch (FeignException ex) {
            LOG.error(
                    "[SETUP_SERVICE] Failed to fetch procedure. id={} status={} body={}",
                    procedureId,
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );

            throw new BadRequestAlertException(
                    "Unable to fetch procedure setup data: " + ex.contentUTF8(),
                    "procedure",
                    "procedureSetup.unreachable"
            );
        }
    }

    private PatientServiceAndProduct buildProcedureBillingItem(
            Long patientId,
            Long encounterId,
            Long sourceId,
            ProcedureSetupDTO setupProcedure,
            String notes
    ) {
        BigDecimal unitPrice = BigDecimal.valueOf(
                setupProcedure.price() == null ? 0L : setupProcedure.price()
        );
        Long quantity = 1L;
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));

        return PatientServiceAndProduct.builder()
                .patientId(patientId)
                .encounterId(encounterId)
                .billingItemType(BillingItemTypes.PROCEDURE)
                .procedureId(setupProcedure.id())
                .serviceSource(ServiceSource.PROCEDURE)
                .sourceId(sourceId)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .discountAmount(BigDecimal.ZERO)
                .exemptionAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .currency(setupProcedure.currency())
                .isBilled(Boolean.FALSE)
                .billingInvoiceId(null)
                .billingInvoiceItemId(null)
                .notes(notes)
                .build();
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
            return new BadRequestAlertException(
                    "Invalid patient id.",
                    "procedure",
                    "patient.invalid"
            );
        }
        if (m.contains("fk_procedure_from_facility")) {
            return new BadRequestAlertException(
                    "Invalid from facility id.",
                    "procedure",
                    "fromFacility.invalid"
            );
        }
        if (m.contains("fk_procedure_to_facility")) {
            return new BadRequestAlertException(
                    "Invalid to facility id.",
                    "procedure",
                    "toFacility.invalid"
            );
        }
        if (m.contains("fk_procedure_from_department")) {
            return new BadRequestAlertException(
                    "Invalid from department id.",
                    "procedure",
                    "fromDepartment.invalid"
            );
        }
        if (m.contains("fk_procedure_to_department")) {
            return new BadRequestAlertException(
                    "Invalid to department id.",
                    "procedure",
                    "toDepartment.invalid"
            );
        }
        if (m.contains("fk_procedure_indication_icd")) {
            return new BadRequestAlertException(
                    "Invalid indication (ICD) id.",
                    "procedure",
                    "indication.invalid"
            );
        }
        if (m.contains("ck_procedure_level_enum")) {
            return new BadRequestAlertException(
                    "Invalid procedure level.",
                    "procedure",
                    "procedureLevel.invalid"
            );
        }
        if (m.contains("ck_procedure_status_enum")) {
            return new BadRequestAlertException(
                    "Invalid procedure status.",
                    "procedure",
                    "status.invalid"
            );
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