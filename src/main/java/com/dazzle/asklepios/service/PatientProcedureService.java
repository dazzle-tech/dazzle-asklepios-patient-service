package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.ProcedureClient;
import com.dazzle.asklepios.client.setup.dto.ProcedureSetupDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.domain.enumeration.ProcedureLevel;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.EncounterPreAuthorizationSyncService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureCreateDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureUpdateDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.errors.PreAuthorizationSubmissionFailedException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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
    private final PreAuthorizationResolutionService preAuthorizationResolutionService;
    private final EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService;
    private final BillingChargeService billingChargeService;
    private final BillingEngineService billingEngineService;
    private final PatientServiceAndProductService patientServiceAndProductService;
    private final PatientItemPricingService patientItemPricingService;

    public PatientProcedureService(
            PatientProcedureRepository procedureRepository,
            PatientRepository patientRepository,
            PatientEncounterRepository patientEncounterRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            ProcedureClient procedureClient,
            FacilityHelper facilityHelper,
            DepartmentHelper departmentHelper,
            PreAuthorizationResolutionService preAuthorizationResolutionService,
            EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService,
            BillingChargeService billingChargeService,
            @Lazy BillingEngineService billingEngineService,
            @Lazy PatientServiceAndProductService patientServiceAndProductService,
            PatientItemPricingService patientItemPricingService
    ) {
        this.procedureRepository = procedureRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.procedureClient = procedureClient;
        this.facilityHelper = facilityHelper;
        this.departmentHelper = departmentHelper;
        this.preAuthorizationResolutionService = preAuthorizationResolutionService;
        this.encounterPreAuthorizationSyncService = encounterPreAuthorizationSyncService;
        this.billingChargeService = billingChargeService;
        this.billingEngineService = billingEngineService;
        this.patientServiceAndProductService = patientServiceAndProductService;
        this.patientItemPricingService = patientItemPricingService;
    }

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (username == null) {
            throw new BadRequestAlertException(
                    "No authenticated user",
                    "procedure",
                    "unauthenticated"
            );
        }
        return username;
    }

    @Transactional(noRollbackFor = PreAuthorizationSubmissionFailedException.class)
    public PatientProcedure create(PatientProcedureCreateDTO procedureCreateDTO) {
        Patient patient = patientRepository.findById(procedureCreateDTO.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + procedureCreateDTO.patientId(),
                        "procedure",
                        "patient.notfound"
                ));

        PatientEncounter encounter = patientEncounterRepository.findById(procedureCreateDTO.encounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found with id " + procedureCreateDTO.encounterId(),
                        "procedure",
                        "encounter.notfound"
                ));

        BillingCoverageType encounterCoverage = resolveEncounterCoverageType(encounter);

        LOG.info(
                "[PROCEDURE_CREATE] Step 1 — encounter coverage. encounterId={} coverageType={} patientInsuranceId={}",
                encounter.getId(),
                encounterCoverage,
                encounter.getPatientInsuranceId()
        );

        ProcedureSetupDTO setupProcedure = fetchProcedureSetup(procedureCreateDTO.procedureId());
        validateProcedureSetupForBilling(setupProcedure);

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
                .result(procedureCreateDTO.result())
                .status(ProcStatus.REQUESTED)
                .build();

        try {
            PatientProcedure savedProcedure = procedureRepository.saveAndFlush(procedureEntity);

            LOG.info(
                    "[PROCEDURE_CREATE] Step 2 — procedure saved. procedureId={} setupProcedureId={}",
                    savedProcedure.getId(),
                    setupProcedure.id()
            );

            PatientServiceAndProduct billingItem = buildProcedureBillingItem(
                    patient.getId(),
                    encounter,
                    savedProcedure.getId(),
                    setupProcedure,
                    procedureCreateDTO.notes(),
                    encounterCoverage
            );

            PatientServiceAndProduct savedBillingItem =
                    patientServiceAndProductRepository.saveAndFlush(billingItem);

            LOG.info(
                    "[PROCEDURE_CREATE] Step 3 — billing item saved. pspId={} preAuthorizationStatus={} waseelSbsCode={}",
                    savedBillingItem.getId(),
                    savedBillingItem.getPreAuthorizationStatus(),
                    savedBillingItem.getWaseelSbsCode()
            );

            completeProcedureBillingFlow(
                    encounter,
                    savedProcedure,
                    savedBillingItem,
                    encounterCoverage
            );

            return savedProcedure;

        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    public PatientProcedure update(Long id, PatientProcedureUpdateDTO procedureUpdateDTO) {
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
        procedureEntity.setResult(procedureUpdateDTO.result());

        try {
            return procedureRepository.saveAndFlush(procedureEntity);
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    public PatientProcedure cancel(Long id, String reason) {
        PatientProcedure procedureEntity = procedureRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Procedure not found with id " + id,
                        "procedure",
                        "notfound"
                ));

        String cancelReason =
                reason == null || reason.isBlank()
                        ? "Procedure cancelled"
                        : reason;

        try {
            patientServiceAndProductService.cancelBySource(
                    ServiceSource.PROCEDURE,
                    procedureEntity.getId(),
                    BillingItemTypes.PROCEDURE,
                    cancelReason
            );

            procedureEntity.setStatus(ProcStatus.CANCELLED);
            procedureEntity.setCancelledDate(Instant.now());
            procedureEntity.setCancelledBy(currentUsername());
            procedureEntity.setCancellationReason(cancelReason);

            return procedureRepository.saveAndFlush(procedureEntity);
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
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
        return procedureRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Procedure not found",
                        "procedure",
                        "notfound"
                ));
    }

    /**
     * Step 4 — after procedure + billing item are saved:
     * - Self pay, or insurance without pre-auth → run billing engine immediately.
     * - Insurance with pending pre-auth → submit to Waseel synchronously.
     *   Waseel payload errors keep the clinical order so it can be resubmitted.
     */
    private void completeProcedureBillingFlow(
            PatientEncounter encounter,
            PatientProcedure procedure,
            PatientServiceAndProduct billingItem,
            BillingCoverageType encounterCoverage
    ) {
        if (encounterCoverage != BillingCoverageType.INSURANCE) {
            LOG.info(
                    "[PROCEDURE_CREATE] Step 4 — self pay, continuing billing. encounterId={} pspId={}",
                    encounter.getId(),
                    billingItem.getId()
            );
            billProcedureItem(billingItem, encounter);
            return;
        }

        if (billingItem.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL) {
            LOG.info(
                    "[PROCEDURE_CREATE] Step 4 — insurance pre-auth required, submitting to Waseel. encounterId={} pspId={}",
                    encounter.getId(),
                    billingItem.getId()
            );
            try {
                encounterPreAuthorizationSyncService.submitPendingPreAuthorizationOrThrow(
                        encounter.getId()
                );
            } catch (PreAuthorizationSubmissionFailedException ex) {
                throw ex;
            } catch (BadRequestAlertException ex) {
                throw mapPreAuthorizationFailure(ex, "procedure");
            }

            procedure.setStatus(ProcStatus.WAITING_PRE_AUTHORIZATION);
            procedureRepository.saveAndFlush(procedure);

            LOG.info(
                    "[PROCEDURE_CREATE] Step 4 — pre-auth submitted. procedureId={} status={}",
                    procedure.getId(),
                    procedure.getStatus()
            );
            return;
        }

        LOG.info(
                "[PROCEDURE_CREATE] Step 4 — insurance without pre-auth, continuing billing. encounterId={} pspId={}",
                encounter.getId(),
                billingItem.getId()
        );
        billProcedureItem(billingItem, encounter);
    }

    private void billProcedureItem(
            PatientServiceAndProduct billingItem,
            PatientEncounter encounter
    ) {
        if (billingItem == null || billingItem.getId() == null || encounter == null) {
            return;
        }

        Long facilityId = encounter.getFacilityId();
        if (facilityId == null) {
            throw new BadRequestAlertException(
                    "Encounter facility is required to bill procedure.",
                    "procedure",
                    "encounter.facility.required"
            );
        }

        if (billingChargeService
                .findActiveChargeLine(billingItem.getId(), encounter.getId())
                .isPresent()) {
            LOG.info(
                    "[PROCEDURE_BILLING] Charge line already exists pspId={} encounterId={}",
                    billingItem.getId(),
                    encounter.getId()
            );
            return;
        }

        BillingOperationResult result =
                billingEngineService.onItemOrdered(
                        billingItem.getId(),
                        facilityId,
                        "PROCEDURE-CREATE:" + billingItem.getId()
                );

        LOG.info(
                "[PROCEDURE_BILLING] pspId={} processed={} chargeLineId={} message={}",
                billingItem.getId(),
                result.processed(),
                result.chargeLineId(),
                result.message()
        );

        if (!result.processed()) {
            throw new BadRequestAlertException(
                    result.message() == null
                            ? "Procedure billing rule did not match."
                            : result.message(),
                    "procedure",
                    "billing.failed"
            );
        }
    }

    private BillingCoverageType resolveEncounterCoverageType(PatientEncounter encounter) {
        if (encounter == null || encounter.getCoverageType() == null) {
            return BillingCoverageType.SELF_PAY;
        }
        return encounter.getCoverageType();
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
            throw new NotFoundAlertException(
                    "Procedure setup not found with id " + procedureId,
                    "procedure",
                    "procedureSetup.notfound"
            );

        } catch (FeignException ex) {
            throw new BadRequestAlertException(
                    "Unable to fetch procedure setup data: " + ex.contentUTF8(),
                    "procedure",
                    "procedureSetup.unreachable"
            );
        }
    }

    private void validateProcedureSetupForBilling(ProcedureSetupDTO setupProcedure) {
        if (setupProcedure == null || setupProcedure.id() == null) {
            throw new BadRequestAlertException(
                    "Procedure setup is required.",
                    "procedure",
                    "procedureSetup.required"
            );
        }

        if (setupProcedure.price() == null) {
            throw new BadRequestAlertException(
                    "Procedure price is required.",
                    "procedure",
                    "price.required"
            );
        }

        if (setupProcedure.currency() == null) {
            throw new BadRequestAlertException(
                    "Procedure currency is required.",
                    "procedure",
                    "currency.required"
            );
        }
    }

    /**
     * Step 2 (billing build) — self pay: no pre-auth changes.
     * Insurance: check price list/payor plan, attach insurance + SBS code, set pre-auth status.
     */
    private PatientServiceAndProduct buildProcedureBillingItem(
            Long patientId,
            PatientEncounter encounter,
            Long sourceId,
            ProcedureSetupDTO setupProcedure,
            String notes,
            BillingCoverageType encounterCoverage
    ) {
        Long encounterId = encounter.getId();
        Long quantity = 1L;

        PatientServiceAndProduct.PatientServiceAndProductBuilder builder = PatientServiceAndProduct.builder()
                .patientId(patientId)
                .encounterId(encounterId)
                .billingItemType(BillingItemTypes.PROCEDURE)
                .brandMedicationId(null)
                .diagnosticTestId(null)
                .serviceId(null)
                .procedureId(setupProcedure.id())
                .serviceSource(ServiceSource.PROCEDURE)
                .sourceId(sourceId)
                .quantity(quantity)
                .unitPrice(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .exemptionAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .currency(setupProcedure.currency())
                .isBilled(Boolean.FALSE)
                .billingInvoiceId(null)
                .billingInvoiceItemId(null)
                .notes(notes);

        if (encounterCoverage != BillingCoverageType.INSURANCE) {
            builder
                    .preAuthorizationStatus(PreAuthorizationStatus.NOT_REQUIRED)
                    .preAuthorizationRequired(false)
                    .paymentStatus(PaymentStatus.PENDING);
        } else {
            LOG.info(
                    "[PROCEDURE_CREATE] Insurance encounter — checking price list pre-authorization for procedureId={}",
                    setupProcedure.id()
            );

            preAuthorizationResolutionService.resolveAndPrepareNewItem(
                    builder,
                    encounterId,
                    BillingItemTypes.PROCEDURE,
                    setupProcedure.id(),
                    null,
                    null,
                    null,
                    true,
                    setupProcedure.currency()
            );

            preAuthorizationResolutionService.enrichWaseelSbsMapping(
                    builder,
                    BillingItemTypes.PROCEDURE,
                    setupProcedure.id()
            );
        }

        PatientServiceAndProduct billingItem = builder.build();
        patientItemPricingService.applyResolvedPricing(billingItem, encounter.getFacilityId());
        return billingItem;
    }

    /**
     * Surfaces the original pre-auth failure reason to the UI
     * (missing diagnosis, LOV mapping, Waseel API body, etc.).
     */
    private BadRequestAlertException mapPreAuthorizationFailure(
            BadRequestAlertException ex,
            String entityName
    ) {
        String errorKey = ex.getErrorKey() != null
                ? ex.getErrorKey()
                : "preAuthorization.failed";
        String title = ex.getBody() != null && ex.getBody().getTitle() != null
                ? ex.getBody().getTitle()
                : ex.getMessage();

        LOG.warn(
                "[PROCEDURE_CREATE] Pre-authorization blocked create. errorKey={} message={}",
                errorKey,
                title
        );

        return new BadRequestAlertException(title, entityName, errorKey);
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);

        String msg = root != null
                ? root.getMessage()
                : exception.getMessage();

        String m = msg != null ? msg.toLowerCase() : "";

        LOG.error("========== REAL DATABASE ERROR ==========");
        LOG.error("{}", msg);
        LOG.error("=========================================", exception);

        if (m.contains("patient_services_and_products") && m.contains("currency")) {
            return new BadRequestAlertException(
                    "Procedure currency is required or invalid.",
                    "procedure",
                    "currency.required"
            );
        }

        if (m.contains("patient_services_and_products") && m.contains("unit_price")) {
            return new BadRequestAlertException(
                    "Procedure price is required.",
                    "procedure",
                    "price.required"
            );
        }

        if (m.contains("patient_services_and_products") && m.contains("service_source")) {
            return new BadRequestAlertException(
                    "Service source is required.",
                    "procedure",
                    "serviceSource.required"
            );
        }

        if (m.contains("patient_services_and_products") && m.contains("billing_item_type")) {
            return new BadRequestAlertException(
                    "Billing item type is required.",
                    "procedure",
                    "billingItemType.required"
            );
        }

        if (m.contains("patient_services_and_products") && m.contains("patient_id")) {
            return new BadRequestAlertException(
                    "Invalid patient id for billing item.",
                    "procedure",
                    "patient.invalid"
            );
        }

        if (m.contains("patient_services_and_products") && m.contains("encounter_id")) {
            return new BadRequestAlertException(
                    "Invalid encounter id for billing item.",
                    "procedure",
                    "encounter.invalid"
            );
        }

        if (m.contains("fk_psp_procedure")) {
            return new BadRequestAlertException(
                    "Procedure setup does not exist in billing table reference.",
                    "procedure",
                    "procedure.invalid"
            );
        }

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
                    "Invalid indication ICD id.",
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

        if (m.contains("ck_billing_charge_line_allocation_balance")) {
            return new BadRequestAlertException(
                    "Cannot cancel procedure billing because charge-line allocation balances are inconsistent. "
                            + "Please retry or contact support if this persists.",
                    "procedure",
                    "billing.cancel.allocationBalance"
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
                msg != null ? msg : "Database constraint violated while saving procedure.",
                "procedure",
                "db.constraint"
        );
    }
}
