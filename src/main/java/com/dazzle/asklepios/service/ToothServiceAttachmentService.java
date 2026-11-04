package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.ToothService;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.EncounterPreAuthorizationSyncService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import com.dazzle.asklepios.repository.DentalChartRepository;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.ToothServiceRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.service.dto.dentalChart.DentalChartResponseDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothServiceSaveDTO;
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

@Service
@Transactional
public class ToothServiceAttachmentService {

    private static final Logger LOG = LoggerFactory.getLogger(ToothServiceAttachmentService.class);

    private final DentalChartToothRepository dentalChartToothRepository;
    private final DentalChartRepository dentalChartRepository;
    private final ToothServiceRepository toothServiceRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final ServiceHelper serviceHelper;
    private final InsurancePriceListCoverageService insurancePriceListCoverageService;
    private final PreAuthorizationResolutionService preAuthorizationResolutionService;
    private final EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService;
    private final BillingEngineService billingEngineService;
    private final PatientServiceAndProductService patientServiceAndProductService;
    private final DentalChartService dentalChartService;

    public ToothServiceAttachmentService(
            DentalChartToothRepository dentalChartToothRepository,
            DentalChartRepository dentalChartRepository,
            ToothServiceRepository toothServiceRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
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
        this.toothServiceRepository = toothServiceRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.serviceHelper = serviceHelper;
        this.insurancePriceListCoverageService = insurancePriceListCoverageService;
        this.preAuthorizationResolutionService = preAuthorizationResolutionService;
        this.encounterPreAuthorizationSyncService = encounterPreAuthorizationSyncService;
        this.billingEngineService = billingEngineService;
        this.patientServiceAndProductService = patientServiceAndProductService;
        this.dentalChartService = dentalChartService;
    }

    public DentalChartResponseDTO saveManualToothService(ToothServiceSaveDTO dto) {
        DentalChartTooth tooth = getTooth(dto.toothId());
        if (tooth.isMissing()) {
            throw new BadRequestAlertException(
                    "Cannot modify a missing tooth",
                    "toothService",
                    "toothMissing"
            );
        }

        DentalChart chart = getChart(tooth.getChartId());

        ServiceSetupDTO setupService = serviceHelper.getService(dto.serviceId());
        if (setupService.price() == null) {
            throw new BadRequestAlertException(
                    "Service '" + setupService.name() + "' has no price configured",
                    "toothService",
                    "priceRequired"
            );
        }

        ToothService toothServiceRow = ToothService.builder()
                .toothId(tooth.getId())
                .serviceId(dto.serviceId())
                .source(ServiceSource.DENTAL_CHART_MANUAL_SERVICE)
                .toothActionId(null)
                .price(setupService.price())
                .cancelled(false)
                .build();
        toothServiceRow = toothServiceRepository.saveAndFlush(toothServiceRow);

        String notes = "For Tooth: " + tooth.getToothNumber();
        PatientServiceAndProduct billingItem = buildToothServiceBillingItem(chart, toothServiceRow, setupService, notes);
        billingItem = patientServiceAndProductRepository.saveAndFlush(billingItem);

        completeDentalBillingFlow(chart, billingItem);

        return dentalChartService.getChartById(tooth.getChartId());
    }

    public DentalChartResponseDTO cancelToothService(Long id, String cancellationReason) {
        ToothService toothServiceRow = toothServiceRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Tooth service not found: " + id,
                        "toothService",
                        "notfound"
                ));

        if (toothServiceRow.isCancelled()) {
            throw new BadRequestAlertException(
                    "Tooth service is already cancelled",
                    "toothService",
                    "alreadyCancelled"
            );
        }

        if (cancellationReason == null || cancellationReason.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Cancellation reason is required",
                    "toothService",
                    "cancellationReasonRequired"
            );
        }

        String currentUser = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));

        toothServiceRow.setCancelled(true);
        toothServiceRow.setCancelledBy(currentUser);
        toothServiceRow.setCancelledDate(Instant.now());
        toothServiceRow.setCancellationReason(cancellationReason);
        toothServiceRepository.save(toothServiceRow);

        patientServiceAndProductService.cancelBySource(
                toothServiceRow.getSource(),
                toothServiceRow.getId(),
                BillingItemTypes.SERVICE,
                cancellationReason
        );

        DentalChartTooth tooth = getTooth(toothServiceRow.getToothId());
        return dentalChartService.getChartById(tooth.getChartId());
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
            Currency currency
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
                    "toothService",
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
                    "toothService",
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

        return new BadRequestAlertException(title, "toothService", errorKey);
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
