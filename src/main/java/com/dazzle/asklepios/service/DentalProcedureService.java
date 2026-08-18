package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.ProcedureClient;
import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.client.setup.dto.ProcedureSetupDTO;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.DentalProcedure;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.EncounterPreAuthorizationSyncService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import com.dazzle.asklepios.repository.DentalProcedureRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.service.dto.billing.InsurancePriceListCoverageCheckResult;
import com.dazzle.asklepios.service.dto.dentalProcedure.DentalProcedureCreateDTO;
import com.dazzle.asklepios.service.dto.dentalProcedure.DentalProcedureUpdateDTO;
import com.dazzle.asklepios.service.helper.CDTCodeHelper;
import com.dazzle.asklepios.service.helper.ProcedureHelper;
import com.dazzle.asklepios.service.helper.ServiceHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@Transactional
public class DentalProcedureService {

    private static final Logger LOG = LoggerFactory.getLogger(DentalProcedureService.class);

    private final DentalProcedureRepository dentalProcedureRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final ServiceHelper serviceHelper;
    private final ProcedureHelper procedureHelper;
    private final CDTCodeHelper cdtCodeHelper;
    private final ProcedureClient procedureClient;
    private final ServiceClient serviceClient;
    private final EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService;
    private final PreAuthorizationResolutionService preAuthorizationResolutionService;
    private final BillingEngineService billingEngineService;
    private final PatientServiceAndProductService patientServiceAndProductService;
    private final InsurancePriceListCoverageService insurancePriceListCoverageService;

    public DentalProcedureService(
            DentalProcedureRepository dentalProcedureRepository,
            PatientRepository patientRepository,
            PatientEncounterRepository patientEncounterRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            ServiceHelper serviceHelper,
            ProcedureHelper procedureHelper,
            CDTCodeHelper cdtCodeHelper,
            ProcedureClient procedureClient,
            ServiceClient serviceClient,
            EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService,
            PreAuthorizationResolutionService preAuthorizationResolutionService,
            @Lazy BillingEngineService billingEngineService,
            @Lazy PatientServiceAndProductService patientServiceAndProductService,
            InsurancePriceListCoverageService insurancePriceListCoverageService
    ) {
        this.dentalProcedureRepository = dentalProcedureRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.serviceHelper = serviceHelper;
        this.procedureHelper = procedureHelper;
        this.cdtCodeHelper = cdtCodeHelper;
        this.procedureClient = procedureClient;
        this.serviceClient = serviceClient;
        this.encounterPreAuthorizationSyncService = encounterPreAuthorizationSyncService;
        this.preAuthorizationResolutionService = preAuthorizationResolutionService;
        this.billingEngineService = billingEngineService;
        this.patientServiceAndProductService = patientServiceAndProductService;
        this.insurancePriceListCoverageService = insurancePriceListCoverageService;
    }

    public DentalProcedure create(DentalProcedureCreateDTO dto) {
        LOG.info(
                "Create DentalProcedure started. patientId={}, encounterId={}, procedureId={}, serviceId={}, cdtCodeId={}",
                dto.patientId(),
                dto.encounterId(),
                dto.procedureId(),
                dto.serviceId(),
                dto.cdtCodeId()
        );

        Patient patient = getPatient(dto.patientId());
        PatientEncounter encounter = getEncounter(dto.encounterId());

        validateCreateReferences(dto);

        ProcedureSetupDTO setupProcedure = fetchProcedureSetup(dto.procedureId());

        ServiceSetupDTO setupService = null;
        if (dto.serviceId() != null) {
            setupService = fetchServiceSetup(dto.serviceId());
        }

        DentalProcedure entity = buildDentalProcedure(dto, patient, encounter);

        try {
            List<InsurancePriceListCoverageCheckResult> coverageChecks =
                    new ArrayList<>();
            coverageChecks.add(
                    insurancePriceListCoverageService.check(
                            encounter.getId(),
                            BillingItemTypes.PROCEDURE,
                            null,
                            setupProcedure.id(),
                            null,
                            null,
                            setupProcedure.currency()
                    )
            );
            if (setupService != null) {
                coverageChecks.add(
                        insurancePriceListCoverageService.check(
                                encounter.getId(),
                                BillingItemTypes.SERVICE,
                                setupService.id(),
                                null,
                                null,
                                null,
                                setupService.currency()
                        )
                );
            }
            insurancePriceListCoverageService.requireAllCoveredOrAcknowledged(
                    coverageChecks,
                    dto.acceptUncoveredAsCash()
            );

            DentalProcedure saved = dentalProcedureRepository.saveAndFlush(entity);

            PatientServiceAndProduct procedureBilling =
                    createProcedureBillingItem(saved, setupProcedure, dto.notes(), dto.surface());
            PatientServiceAndProduct serviceBilling =
                    createServiceBillingItemIfExists(saved, setupService, dto.notes(), dto.surface());

            completeDentalBillingFlow(encounter, procedureBilling, serviceBilling);

            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException e) {
            throw handleConstraintViolation(e);
        }
    }

    @Transactional(readOnly = true)
    public Page<DentalProcedure> findAllByPatientId(Long patientId, boolean showCancelled, Pageable pageable) {
        return showCancelled
                ? dentalProcedureRepository.findByPatientId(patientId, pageable)
                : dentalProcedureRepository.findByPatientIdAndCancelledFalse(patientId, pageable);
    }

    public DentalProcedure update(DentalProcedureUpdateDTO dto) {
        DentalProcedure entity = getDentalProcedure(dto.id());

        if (entity.isCancelled()) {
            throw new BadRequestAlertException(
                    "cannotUpdateCancelled",
                    "dentalProcedure",
                    "Cannot update a cancelled dental procedure"
            );
        }

        Optional<PatientServiceAndProduct> procedureBillingItem =
                findBillingItem(entity.getId(), BillingItemTypes.PROCEDURE);

        Optional<PatientServiceAndProduct> serviceBillingItem =
                findBillingItem(entity.getId(), BillingItemTypes.SERVICE);

        if (procedureBillingItem.isPresent() && Boolean.TRUE.equals(procedureBillingItem.get().getIsBilled())) {
            throw new BadRequestAlertException(
                    "procedureAlreadyBilled",
                    "dentalProcedure",
                    "Cannot update dental procedure because procedure billing item is already billed"
            );
        }

        if (serviceBillingItem.isPresent() && Boolean.TRUE.equals(serviceBillingItem.get().getIsBilled())) {
            throw new BadRequestAlertException(
                    "serviceAlreadyBilled",
                    "dentalProcedure",
                    "Cannot update dental procedure because service billing item is already billed"
            );
        }

        validateUpdateReferences(dto);

        ProcedureSetupDTO setupProcedure = fetchProcedureSetup(dto.procedureId());

        ServiceSetupDTO setupService = null;
        if (dto.serviceId() != null) {
            setupService = fetchServiceSetup(dto.serviceId());
        }

        deleteBillingItemIfNotBilled(procedureBillingItem);
        deleteBillingItemIfNotBilled(serviceBillingItem);

        updateDentalProcedureFields(entity, dto);

        try {
            DentalProcedure updated = dentalProcedureRepository.saveAndFlush(entity);

            PatientServiceAndProduct procedureBilling =
                    createProcedureBillingItem(updated, setupProcedure, dto.notes(), dto.surface());
            PatientServiceAndProduct serviceBilling =
                    createServiceBillingItemIfExists(updated, setupService, dto.notes(), dto.surface());

            completeDentalBillingFlow(updated.getEncounter(), procedureBilling, serviceBilling);

            return updated;
        } catch (DataIntegrityViolationException | JpaSystemException e) {
            throw handleConstraintViolation(e);
        }
    }

    public DentalProcedure cancel(Long id) {
        SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));

        DentalProcedure entity = getDentalProcedure(id);

        if (entity.isCancelled()) {
            throw new BadRequestAlertException(
                    "DentalProcedure is already cancelled",
                    "dentalProcedure",
                    "alreadyCancelled"
            );
        }

        String cancelReason = "Dental procedure cancelled";

        patientServiceAndProductService.cancelBySource(
                ServiceSource.DENTAL_PROCEDURE,
                entity.getId(),
                BillingItemTypes.PROCEDURE,
                cancelReason
        );
        patientServiceAndProductService.cancelBySource(
                ServiceSource.DENTAL_PROCEDURE,
                entity.getId(),
                BillingItemTypes.SERVICE,
                cancelReason
        );

        entity.setCancelled(true);

        return dentalProcedureRepository.saveAndFlush(entity);
    }

    private Patient getPatient(Long patientId) {
        return patientRepository.findById(patientId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "patientNotFound",
                        "dentalProcedure",
                        "Patient not found with id " + patientId
                ));
    }

    private PatientEncounter getEncounter(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "encounterNotFound",
                        "dentalProcedure",
                        "Encounter not found with id " + encounterId
                ));
    }

    private DentalProcedure getDentalProcedure(Long id) {
        return dentalProcedureRepository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "idNotFound",
                        "dentalProcedure",
                        "DentalProcedure not found with id " + id
                ));
    }

    private void validateCreateReferences(DentalProcedureCreateDTO dto) {
        procedureHelper.validateProcedureExists(dto.procedureId());

        if (dto.serviceId() != null) {
            serviceHelper.validateServiceExists(dto.serviceId());
        }

        if (dto.cdtCodeId() != null) {
            cdtCodeHelper.validateCDTCodeExists(dto.cdtCodeId());
        }
    }

    private void validateUpdateReferences(DentalProcedureUpdateDTO dto) {
        procedureHelper.validateProcedureExists(dto.procedureId());

        if (dto.serviceId() != null) {
            serviceHelper.validateServiceExists(dto.serviceId());
        }

        if (dto.cdtCodeId() != null) {
            cdtCodeHelper.validateCDTCodeExists(dto.cdtCodeId());
        }
    }

    private ProcedureSetupDTO fetchProcedureSetup(Long procedureId) {
        try {
            ProcedureSetupDTO setupProcedure = procedureClient.getProcedure(procedureId);

            if (setupProcedure == null || setupProcedure.id() == null) {
                throw new BadRequestAlertException(
                        "procedureSetupNotFound",
                        "dentalProcedure",
                        "Procedure setup not found with id " + procedureId
                );
            }

            return setupProcedure;
        } catch (FeignException.NotFound e) {
            throw new BadRequestAlertException(
                    "procedureSetupNotFound",
                    "dentalProcedure",
                    "Procedure setup not found with id " + procedureId
            );
        } catch (FeignException e) {
            throw new BadRequestAlertException(
                    "procedureSetupUnreachable",
                    "dentalProcedure",
                    "Unable to fetch procedure setup data"
            );
        }
    }

    private ServiceSetupDTO fetchServiceSetup(Long serviceId) {
        if (serviceId == null) {
            return null;
        }

        try {
            ServiceSetupDTO setupService = serviceClient.getServiceDetails(serviceId);

            if (setupService == null || setupService.id() == null) {
                throw new BadRequestAlertException(
                        "serviceSetupNotFound",
                        "dentalProcedure",
                        "Service setup not found with id " + serviceId
                );
            }

            return setupService;
        } catch (FeignException.NotFound e) {
            throw new BadRequestAlertException(
                    "serviceSetupNotFound",
                    "dentalProcedure",
                    "Service setup not found with id " + serviceId
            );
        } catch (FeignException e) {
            throw new BadRequestAlertException(
                    "serviceSetupUnreachable",
                    "dentalProcedure",
                    "Unable to fetch service setup data"
            );
        }
    }

    private DentalProcedure buildDentalProcedure(
            DentalProcedureCreateDTO dto,
            Patient patient,
            PatientEncounter encounter
    ) {
        return DentalProcedure.builder()
                .patient(patient)
                .encounter(encounter)
                .toothNumber(dto.toothNumber())
                .surface(dto.surface())
                .anesthesiaUsed(dto.anesthesiaUsed())
                .dose(dto.dose())
                .unit(dto.unit())
                .fillingMaterial(dto.fillingMaterial())
                .procedureId(dto.procedureId())
                .serviceId(dto.serviceId())
                .cdtCodeId(dto.cdtCodeId())
                .notes(dto.notes())
                .cancelled(false)
                .build();
    }

    private void updateDentalProcedureFields(DentalProcedure entity, DentalProcedureUpdateDTO dto) {
        entity.setToothNumber(dto.toothNumber());
        entity.setSurface(dto.surface());
        entity.setAnesthesiaUsed(dto.anesthesiaUsed());
        entity.setDose(dto.dose());
        entity.setUnit(dto.unit());
        entity.setFillingMaterial(dto.fillingMaterial());
        entity.setProcedureId(dto.procedureId());
        entity.setServiceId(dto.serviceId());
        entity.setCdtCodeId(dto.cdtCodeId());
        entity.setNotes(dto.notes());
    }

    private PatientServiceAndProduct createProcedureBillingItem(
            DentalProcedure dentalProcedure,
            ProcedureSetupDTO setupProcedure,
            String notes,
            String surface
    ) {
        PatientServiceAndProduct billingItem = buildProcedureBillingItem(
                dentalProcedure,
                setupProcedure,
                notes,
                surface
        );

        return patientServiceAndProductRepository.saveAndFlush(billingItem);
    }

    private PatientServiceAndProduct createServiceBillingItemIfExists(
            DentalProcedure dentalProcedure,
            ServiceSetupDTO setupService,
            String notes,
            String surface
    ) {
        if (setupService == null) {
            return null;
        }

        PatientServiceAndProduct billingItem = buildServiceBillingItem(
                dentalProcedure,
                setupService,
                notes,
                surface
        );

        return patientServiceAndProductRepository.saveAndFlush(billingItem);
    }

    private PatientServiceAndProduct buildProcedureBillingItem(
            DentalProcedure dentalProcedure,
            ProcedureSetupDTO setupProcedure,
            String notes,
            String surface
    ) {
        BigDecimal unitPrice = setupProcedure.price();
        Long quantity = 1L;
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        String billingNotes = buildBillingNotesValue(notes, surface);

        PatientServiceAndProduct.PatientServiceAndProductBuilder builder = PatientServiceAndProduct.builder()
                .patientId(dentalProcedure.getPatient().getId())
                .encounterId(dentalProcedure.getEncounter().getId())
                .billingItemType(BillingItemTypes.PROCEDURE)
                .procedureId(setupProcedure.id())
                .serviceId(null)
                .serviceSource(ServiceSource.DENTAL_PROCEDURE)
                .sourceId(dentalProcedure.getId())
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
                .notes(billingNotes);

        applyDentalInsuranceCoverage(
                builder,
                dentalProcedure.getEncounter().getId(),
                BillingItemTypes.PROCEDURE,
                null,
                setupProcedure.id(),
                null,
                null,
                setupProcedure.currency()
        );

        return builder.build();
    }

    private PatientServiceAndProduct buildServiceBillingItem(
            DentalProcedure dentalProcedure,
            ServiceSetupDTO setupService,
            String notes,
            String surface
    ) {
        BigDecimal unitPrice =setupService.price();
        Long quantity = 1L;
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        String billingNotes = buildBillingNotesValue(notes, surface);

        PatientServiceAndProduct.PatientServiceAndProductBuilder builder = PatientServiceAndProduct.builder()
                .patientId(dentalProcedure.getPatient().getId())
                .encounterId(dentalProcedure.getEncounter().getId())
                .billingItemType(BillingItemTypes.SERVICE)
                .procedureId(null)
                .serviceId(setupService.id())
                .serviceSource(ServiceSource.DENTAL_PROCEDURE)
                .sourceId(dentalProcedure.getId())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .discountAmount(BigDecimal.ZERO)
                .exemptionAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .currency(setupService.currency())
                .isBilled(Boolean.FALSE)
                .billingInvoiceId(null)
                .billingInvoiceItemId(null)
                .notes(billingNotes);

        applyDentalInsuranceCoverage(
                builder,
                dentalProcedure.getEncounter().getId(),
                BillingItemTypes.SERVICE,
                setupService.id(),
                null,
                null,
                null,
                setupService.currency()
        );

        return builder.build();
    }

    private void applyDentalInsuranceCoverage(
            PatientServiceAndProduct.PatientServiceAndProductBuilder builder,
            Long encounterId,
            BillingItemTypes billingItemType,
            Long serviceId,
            Long procedureId,
            Long diagnosticTestId,
            Long brandMedicationId,
            Currency currency
    ) {
        var coverageCheck = insurancePriceListCoverageService.check(
                encounterId,
                billingItemType,
                serviceId,
                procedureId,
                diagnosticTestId,
                brandMedicationId,
                currency
        );
        if (coverageCheck.requiresCashConfirmation()) {
            insurancePriceListCoverageService.applyUncoveredCash(builder, coverageCheck);
            return;
        }

        preAuthorizationResolutionService.resolveAndPrepareNewItem(
                builder,
                encounterId,
                billingItemType,
                procedureId,
                serviceId,
                diagnosticTestId,
                brandMedicationId,
                true,
                currency
        );

        preAuthorizationResolutionService.enrichWaseelSbsMappingForBillingItem(
                builder,
                billingItemType,
                procedureId,
                serviceId,
                diagnosticTestId,
                brandMedicationId
        );
    }

    private Optional<PatientServiceAndProduct> findBillingItem(
            Long dentalProcedureId,
            BillingItemTypes billingItemType
    ) {
        return patientServiceAndProductRepository.findByServiceSourceAndSourceIdAndBillingItemType(
                ServiceSource.DENTAL_PROCEDURE,
                dentalProcedureId,
                billingItemType
        );
    }

    private void deleteBillingItemIfNotBilled(
            Optional<PatientServiceAndProduct> billingItemOptional
    ) {
        if (billingItemOptional.isEmpty()) {
            return;
        }

        PatientServiceAndProduct billingItem = billingItemOptional.get();

        if (Boolean.TRUE.equals(billingItem.getIsBilled())) {
            return;
        }

        patientServiceAndProductRepository.delete(billingItem);
        patientServiceAndProductRepository.flush();
    }

    private String buildBillingNotesValue(String notesValue, String surface) {
        String notes = notesValue == null ? "" : notesValue.trim();
        String surfaceValue = surface == null ? "" : surface.trim();

        if (!surfaceValue.isBlank()) {
            notes = notes.isBlank()
                    ? "Surface: " + surfaceValue
                    : notes + " | Surface: " + surfaceValue;
        }

        return notes;
    }

    private BadRequestAlertException handleConstraintViolation(RuntimeException e) {
        Throwable root = getRootCause(e);
        String message = root != null ? root.getMessage() : e.getMessage();
        String msgLower = message != null ? message.toLowerCase() : "";

        if (msgLower.contains("fk_dental_procedure_procedure")) {
            return new BadRequestAlertException(
                    "procedureNotFound",
                    "dentalProcedure",
                    "The specified procedure does not exist"
            );
        }

        if (msgLower.contains("fk_dental_procedure_service")) {
            return new BadRequestAlertException(
                    "serviceNotFound",
                    "dentalProcedure",
                    "The specified service does not exist"
            );
        }

        if (msgLower.contains("fk_dental_procedure_cdt_code")) {
            return new BadRequestAlertException(
                    "cdtCodeNotFound",
                    "dentalProcedure",
                    "The specified CDT code does not exist"
            );
        }

        return new BadRequestAlertException(
                "db.constraint",
                "dentalProcedure",
                "Database constraint violated while saving dental procedure"
        );
    }

    private void completeDentalBillingFlow(
            PatientEncounter encounter,
            PatientServiceAndProduct procedureBilling,
            PatientServiceAndProduct serviceBilling
    ) {
        List<PatientServiceAndProduct> items = new ArrayList<>();
        if (procedureBilling != null) {
            items.add(procedureBilling);
        }
        if (serviceBilling != null) {
            items.add(serviceBilling);
        }

        boolean needsPreAuth = items.stream().anyMatch(item ->
                item.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL
        );

        if (needsPreAuth) {
            LOG.info(
                    "[DENTAL_PROCEDURE] Pre-auth required — submitting to Waseel. encounterId={}",
                    encounter.getId()
            );
            try {
                encounterPreAuthorizationSyncService.submitPendingPreAuthorizationOrThrow(
                        encounter.getId()
                );
            } catch (BadRequestAlertException ex) {
                throw mapPreAuthorizationFailure(ex);
            }
            return;
        }

        for (PatientServiceAndProduct item : items) {
            billDentalItem(item, encounter);
        }
    }

    private void billDentalItem(
            PatientServiceAndProduct item,
            PatientEncounter encounter
    ) {
        if (item == null || item.getId() == null || encounter == null) {
            return;
        }

        Long facilityId = encounter.getFacilityId();
        if (facilityId == null) {
            throw new BadRequestAlertException(
                    "Encounter facility is required to bill dental item.",
                    "dentalProcedure",
                    "encounter.facility.required"
            );
        }

        BillingOperationResult result = billingEngineService.onItemOrdered(
                item.getId(),
                facilityId,
                "DENTAL-CREATE:" + item.getId()
        );

        LOG.info(
                "[DENTAL_PROCEDURE] Billed item. pspId={} type={} processed={} message={}",
                item.getId(),
                item.getBillingItemType(),
                result.processed(),
                result.message()
        );

        if (!result.processed()) {
            throw new BadRequestAlertException(
                    result.message() == null
                            ? "Dental billing rule did not match."
                            : result.message(),
                    "dentalProcedure",
                    "billing.failed"
            );
        }
    }

    private BadRequestAlertException mapPreAuthorizationFailure(BadRequestAlertException ex) {
        String errorKey = ex.getErrorKey() != null
                ? ex.getErrorKey()
                : "preAuthorization.failed";
        String title = ex.getBody() != null && ex.getBody().getTitle() != null
                ? ex.getBody().getTitle()
                : ex.getMessage();

        LOG.warn(
                "[DENTAL_PROCEDURE] Pre-authorization blocked save. errorKey={} message={}",
                errorKey,
                title
        );

        return new BadRequestAlertException(title, "dentalProcedure", errorKey);
    }
}