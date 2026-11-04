package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.CdtServiceMappingDTO;
import com.dazzle.asklepios.client.setup.dto.DentalActionSetupDTO;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.ToothActionLog;
import com.dazzle.asklepios.domain.ToothCdt;
import com.dazzle.asklepios.domain.ToothService;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.ToothActionLogType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.EncounterPreAuthorizationSyncService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import com.dazzle.asklepios.repository.DentalChartRepository;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.ToothActionLogRepository;
import com.dazzle.asklepios.repository.ToothActionRepository;
import com.dazzle.asklepios.repository.ToothCdtRepository;
import com.dazzle.asklepios.repository.ToothServiceRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.service.dto.dentalChart.DentalChartResponseDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothActionSaveDTO;
import com.dazzle.asklepios.service.helper.CDTCodeHelper;
import com.dazzle.asklepios.service.helper.CdtServiceMappingHelper;
import com.dazzle.asklepios.service.helper.DentalActionHelper;
import com.dazzle.asklepios.service.helper.ServiceHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ToothActionService {

    private static final Logger LOG = LoggerFactory.getLogger(ToothActionService.class);

    private final DentalChartToothRepository dentalChartToothRepository;
    private final DentalChartRepository dentalChartRepository;
    private final ToothActionRepository toothActionRepository;
    private final ToothActionLogRepository toothActionLogRepository;
    private final ToothCdtRepository toothCdtRepository;
    private final ToothServiceRepository toothServiceRepository;
    private final DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final DentalActionHelper dentalActionHelper;
    private final CDTCodeHelper cdtCodeHelper;
    private final CdtServiceMappingHelper cdtServiceMappingHelper;
    private final ServiceHelper serviceHelper;
    private final InsurancePriceListCoverageService insurancePriceListCoverageService;
    private final PreAuthorizationResolutionService preAuthorizationResolutionService;
    private final EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService;
    private final BillingEngineService billingEngineService;
    private final PatientServiceAndProductService patientServiceAndProductService;
    private final DentalChartService dentalChartService;

    public ToothActionService(
            DentalChartToothRepository dentalChartToothRepository,
            DentalChartRepository dentalChartRepository,
            ToothActionRepository toothActionRepository,
            ToothActionLogRepository toothActionLogRepository,
            ToothCdtRepository toothCdtRepository,
            ToothServiceRepository toothServiceRepository,
            DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            DentalActionHelper dentalActionHelper,
            CDTCodeHelper cdtCodeHelper,
            CdtServiceMappingHelper cdtServiceMappingHelper,
            ServiceHelper serviceHelper,
            InsurancePriceListCoverageService insurancePriceListCoverageService,
            PreAuthorizationResolutionService preAuthorizationResolutionService,
            EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService,
            @Lazy BillingEngineService billingEngineService,
            @Lazy PatientServiceAndProductService patientServiceAndProductService,
            DentalChartService dentalChartService
    ) {
        this.dentalChartToothRepository = dentalChartToothRepository;
        this.dentalChartRepository = dentalChartRepository;
        this.toothActionRepository = toothActionRepository;
        this.toothActionLogRepository = toothActionLogRepository;
        this.toothCdtRepository = toothCdtRepository;
        this.toothServiceRepository = toothServiceRepository;
        this.dentalPlannedTreatmentRepository = dentalPlannedTreatmentRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.dentalActionHelper = dentalActionHelper;
        this.cdtCodeHelper = cdtCodeHelper;
        this.cdtServiceMappingHelper = cdtServiceMappingHelper;
        this.serviceHelper = serviceHelper;
        this.insurancePriceListCoverageService = insurancePriceListCoverageService;
        this.preAuthorizationResolutionService = preAuthorizationResolutionService;
        this.encounterPreAuthorizationSyncService = encounterPreAuthorizationSyncService;
        this.billingEngineService = billingEngineService;
        this.patientServiceAndProductService = patientServiceAndProductService;
        this.dentalChartService = dentalChartService;
    }

    public DentalChartResponseDTO saveToothAction(ToothActionSaveDTO dto) {
        DentalChartTooth tooth = getTooth(dto.toothId());
        if (tooth.isMissing()) {
            throw new BadRequestAlertException(
                    "Cannot modify a missing tooth",
                    "toothAction",
                    "toothMissing"
            );
        }

        DentalChart chart = getChart(tooth.getChartId());

        DentalActionSetupDTO action = dentalActionHelper.getDentalAction(dto.dentalActionId());

        if (!dto.existing() && dto.cdtCodeId() != null && "TREATMENT".equalsIgnoreCase(action.type())) {
            cdtCodeHelper.validateCDTCodeExists(dto.cdtCodeId());
        }

        Optional<ToothAction> existingActionForSlot = toothActionRepository
                .findByToothIdAndDentalActionIdAndSurfaceAndCancelledFalse(dto.toothId(), dto.dentalActionId(), dto.surface());
        if (existingActionForSlot.isPresent()
                && (dto.id() == null || !existingActionForSlot.get().getId().equals(dto.id()))) {
            throw new BadRequestAlertException(
                    "Action already applied to this tooth surface",
                    "toothAction",
                    "actionAlreadyApplied"
            );
        }

        if (dto.cdtCodeId() != null) {
            Optional<ToothCdt> existingCdtForSlot = toothCdtRepository
                    .findByToothIdAndCdtCodeIdAndSurfaceAndCancelledFalse(dto.toothId(), dto.cdtCodeId(), dto.surface());
            if (existingCdtForSlot.isPresent()) {
                throw new BadRequestAlertException(
                        "CDT already applied to this tooth surface",
                        "toothAction",
                        "cdtAlreadyApplied"
                );
            }
        }

        boolean isUpdate = dto.id() != null;
        ToothAction toothAction;
        if (isUpdate) {
            toothAction = toothActionRepository.findById(dto.id())
                    .orElseThrow(() -> new NotFoundAlertException(
                            "Tooth action not found: " + dto.id(),
                            "toothAction",
                            "notfound"
                    ));
            if (toothAction.isCancelled()) {
                throw new BadRequestAlertException(
                        "Cannot modify a cancelled tooth action",
                        "toothAction",
                        "alreadyCancelled"
                );
            }
            toothAction.setDentalActionId(dto.dentalActionId());
            toothAction.setNote(dto.note());
            toothAction.setSurface(dto.surface());
            toothAction.setExisting(dto.existing());
            toothAction.setImageName(action.imageName());
            toothAction.setToothNumber(tooth.getToothNumber());
            toothAction.setAnesthesiaUsed(dto.anesthesiaUsed());
            toothAction.setDose(dto.dose());
        } else {
            toothAction = ToothAction.builder()
                    .toothId(tooth.getId())
                    .dentalActionId(dto.dentalActionId())
                    .note(dto.note())
                    .surface(dto.surface())
                    .existing(dto.existing())
                    .imageName(action.imageName())
                    .toothNumber(tooth.getToothNumber())
                    .anesthesiaUsed(dto.anesthesiaUsed())
                    .dose(dto.dose())
                    .cancelled(false)
                    .build();
        }

        ToothAction savedToothAction = toothActionRepository.saveAndFlush(toothAction);

        if (!dto.existing() && dto.cdtCodeId() != null) {
            handleCdtFanOut(dto, tooth, chart, savedToothAction);
        }

        String currentUser = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));

        ToothActionLog log = ToothActionLog.builder()
                .toothId(tooth.getId())
                .dentalActionId(dto.dentalActionId())
                .logType(isUpdate ? ToothActionLogType.CHANGE : ToothActionLogType.SAVE)
                .logTime(Instant.now())
                .logOwner(currentUser)
                .build();
        toothActionLogRepository.save(log);

        return dentalChartService.getChartById(tooth.getChartId());
    }

    public DentalChartResponseDTO cancelToothAction(Long toothActionId, String cancellationReason) {
        ToothAction toothAction = toothActionRepository.findById(toothActionId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Tooth action not found: " + toothActionId,
                        "toothAction",
                        "notfound"
                ));

        if (toothAction.isCancelled()) {
            throw new BadRequestAlertException(
                    "DentalChart tooth action is already cancelled",
                    "toothAction",
                    "alreadyCancelled"
            );
        }
        if (cancellationReason == null || cancellationReason.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Cancellation reason is required",
                    "toothAction",
                    "cancellationReasonRequired"
            );
        }

        String currentUser = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));

        toothAction.setCancelled(true);
        toothAction.setCancelledBy(currentUser);
        toothAction.setCancelledDate(Instant.now());
        toothAction.setCancellationReason(cancellationReason);
        toothActionRepository.save(toothAction);

        ToothActionLog log = ToothActionLog.builder()
                .toothId(toothAction.getToothId())
                .dentalActionId(toothAction.getDentalActionId())
                .logType(ToothActionLogType.CANCEL)
                .logTime(Instant.now())
                .logOwner(currentUser)
                .build();
        toothActionLogRepository.save(log);

        for (ToothCdt toothCdt : toothCdtRepository.findByToothActionIdAndCancelledFalse(toothActionId)) {
            toothCdt.setCancelled(true);
            toothCdt.setCancelledBy(currentUser);
            toothCdt.setCancelledDate(Instant.now());
            toothCdt.setCancellationReason(cancellationReason);
            toothCdtRepository.save(toothCdt);
        }

        for (ToothService toothServiceRow : toothServiceRepository.findByToothActionIdAndCancelledFalse(toothActionId)) {
            toothServiceRow.setCancelled(true);
            toothServiceRow.setCancelledBy(currentUser);
            toothServiceRow.setCancelledDate(Instant.now());
            toothServiceRow.setCancellationReason(cancellationReason);
            toothServiceRepository.save(toothServiceRow);

            patientServiceAndProductService.cancelBySource(
                    ServiceSource.DENTAL_CHART_CDT_TREATMENT,
                    toothServiceRow.getId(),
                    BillingItemTypes.SERVICE,
                    cancellationReason
            );
        }

        for (DentalPlannedTreatment plannedTreatment : dentalPlannedTreatmentRepository.findByToothActionIdAndCancelledFalse(toothActionId)) {
            plannedTreatment.setCancelled(true);
            plannedTreatment.setCancelledBy(currentUser);
            plannedTreatment.setCancelledDate(Instant.now());
            plannedTreatment.setCancellationReason(cancellationReason);
            dentalPlannedTreatmentRepository.save(plannedTreatment);
        }

        DentalChartTooth tooth = getTooth(toothAction.getToothId());
        return dentalChartService.getChartById(tooth.getChartId());
    }

    private void handleCdtFanOut(
            ToothActionSaveDTO dto,
            DentalChartTooth tooth,
            DentalChart chart,
            ToothAction savedToothAction
    ) {
        ToothCdt toothCdt = ToothCdt.builder()
                .toothId(tooth.getId())
                .cdtCodeId(dto.cdtCodeId())
                .source(ServiceSource.DENTAL_CHART_CDT_TREATMENT)
                .toothActionId(savedToothAction.getId())
                .surface(dto.surface())
                .cancelled(false)
                .build();
        toothCdtRepository.save(toothCdt);

        List<CdtServiceMappingDTO> mappings = cdtServiceMappingHelper.getServiceMappingsForCdt(dto.cdtCodeId());
        for (CdtServiceMappingDTO mapping : mappings) {
            ServiceSetupDTO setupService = serviceHelper.getService(mapping.serviceId());
            if (setupService.price() == null) {
                throw new BadRequestAlertException(
                        "Service '" + setupService.name() + "' has no price configured",
                        "toothService",
                        "priceRequired"
                );
            }

            ToothService toothServiceRow = ToothService.builder()
                    .toothId(tooth.getId())
                    .serviceId(mapping.serviceId())
                    .source(ServiceSource.DENTAL_CHART_CDT_TREATMENT)
                    .toothActionId(savedToothAction.getId())
                    .price(setupService.price())
                    .cancelled(false)
                    .build();
            toothServiceRow = toothServiceRepository.saveAndFlush(toothServiceRow);

            String notes = "For Tooth: " + tooth.getToothNumber();
            PatientServiceAndProduct billingItem = buildToothServiceBillingItem(
                    chart,
                    toothServiceRow,
                    setupService,
                    notes
            );
            billingItem = patientServiceAndProductRepository.saveAndFlush(billingItem);

            completeDentalBillingFlow(chart, billingItem);
        }

        DentalPlannedTreatment plannedTreatment = DentalPlannedTreatment.builder()
                .patientId(chart.getPatient().getId())
                .encounterId(chart.getEncounter().getId())
                .type(PlannedTreatmentType.CURRENT)
                .cdtCodeId(dto.cdtCodeId())
                .toothId(tooth.getId())
                .note(savedToothAction.getNote())
                .surface(dto.surface())
                .insurance(BigDecimal.ZERO)
                .source(ServiceSource.DENTAL_CHART_CDT_TREATMENT)
                .toothActionId(savedToothAction.getId())
                .cancelled(false)
                .build();
        dentalPlannedTreatmentRepository.save(plannedTreatment);
    }

    private PatientServiceAndProduct buildToothServiceBillingItem(
            DentalChart chart,
            ToothService toothService,
            ServiceSetupDTO setupService,
            String notes
    ) {
        BigDecimal unitPrice = setupService.price();
        Long quantity = 1L;
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));

        PatientServiceAndProduct.PatientServiceAndProductBuilder builder = PatientServiceAndProduct.builder()
                .patientId(chart.getPatient().getId())
                .encounterId(chart.getEncounter().getId())
                .billingItemType(BillingItemTypes.SERVICE)
                .procedureId(null)
                .serviceId(setupService.id())
                .serviceSource(toothService.getSource())
                .sourceId(toothService.getId())
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
                .notes(notes);

        applyDentalInsuranceCoverage(
                builder,
                chart.getEncounter().getId(),
                BillingItemTypes.SERVICE,
                setupService.id(),
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
            com.dazzle.asklepios.domain.enumeration.Currency currency
    ) {
        var coverageCheck = insurancePriceListCoverageService.check(
                encounterId,
                billingItemType,
                serviceId,
                procedureId,
                null,
                null,
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
                null,
                null,
                true,
                currency
        );

        preAuthorizationResolutionService.enrichWaseelSbsMappingForBillingItem(
                builder,
                billingItemType,
                procedureId,
                serviceId,
                null,
                null
        );
    }

    private void completeDentalBillingFlow(DentalChart chart, PatientServiceAndProduct item) {
        if (item.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL) {
            LOG.info(
                    "[DENTAL_CHART_TOOTH_SERVICE] Pre-auth required — submitting to Waseel. encounterId={}",
                    chart.getEncounter().getId()
            );
            try {
                encounterPreAuthorizationSyncService.submitPendingPreAuthorizationOrThrow(chart.getEncounter().getId());
            } catch (BadRequestAlertException ex) {
                throw mapPreAuthorizationFailure(ex);
            }
            return;
        }

        billDentalItem(item, chart);
    }

    private void billDentalItem(PatientServiceAndProduct item, DentalChart chart) {
        if (item == null || item.getId() == null) {
            return;
        }

        Long facilityId = chart.getEncounter().getFacilityId();
        if (facilityId == null) {
            throw new BadRequestAlertException(
                    "Encounter facility is required to bill dental item.",
                    "toothAction",
                    "encounter.facility.required"
            );
        }

        BillingOperationResult result = billingEngineService.onItemOrdered(
                item.getId(),
                facilityId,
                "DENTAL-CHART-TOOTH-SERVICE-CREATE:" + item.getId()
        );

        LOG.info(
                "[DENTAL_CHART_TOOTH_SERVICE] Billed item. pspId={} type={} processed={} message={}",
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
                    "toothAction",
                    "billing.failed"
            );
        }
    }

    private BadRequestAlertException mapPreAuthorizationFailure(BadRequestAlertException ex) {
        String errorKey = ex.getErrorKey() != null ? ex.getErrorKey() : "preAuthorization.failed";
        String title = ex.getBody() != null && ex.getBody().getTitle() != null
                ? ex.getBody().getTitle()
                : ex.getMessage();

        LOG.warn(
                "[DENTAL_CHART_TOOTH_SERVICE] Pre-authorization blocked save. errorKey={} message={}",
                errorKey,
                title
        );

        return new BadRequestAlertException(title, "toothAction", errorKey);
    }

    private DentalChartTooth getTooth(Long toothId) {
        return dentalChartToothRepository.findById(toothId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Dental chart tooth not found: " + toothId,
                        "dentalChartTooth",
                        "notfound"
                ));
    }

    private DentalChart getChart(Long chartId) {
        return dentalChartRepository.findById(chartId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Dental chart not found: " + chartId,
                        "dentalChart",
                        "notfound"
                ));
    }
}
