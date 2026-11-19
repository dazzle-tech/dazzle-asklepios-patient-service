package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.ProcedureSetupDTO;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.EncounterPreAuthorizationSyncService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.service.dto.billing.InsurancePriceListCoverageCheckResult;
import com.dazzle.asklepios.service.helper.ProcedureHelper;
import com.dazzle.asklepios.service.helper.ServiceHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Charges a completed Dental Care → Treatment Plan item to the encounter it was performed in.
 * <p>
 * Follows the live {@code DentalProcedureService} billing pattern: one Products & Services line, then the
 * Billing Engine unchanged. The engine prices the line (price list, else setup base price) and applies the
 * billing discount rules, so the Treatment Plan discount is never applied to the charge a second time.
 * <p>
 * Runs inside the caller's transaction ({@code MANDATORY}): when billing fails the exception rolls back the
 * completion, its history, the chart effect and this line together.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class DentalPlannedTreatmentBillingService {

    private static final Logger LOG = LoggerFactory.getLogger(DentalPlannedTreatmentBillingService.class);
    private static final String ENTITY = "dentalPlannedTreatment";

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final ProcedureHelper procedureHelper;
    private final ServiceHelper serviceHelper;
    private final EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService;
    private final PreAuthorizationResolutionService preAuthorizationResolutionService;
    private final BillingEngineService billingEngineService;
    private final InsurancePriceListCoverageService insurancePriceListCoverageService;
    private final ReopenSessionAuditContext reopenSessionAuditContext;

    public DentalPlannedTreatmentBillingService(
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            ProcedureHelper procedureHelper,
            ServiceHelper serviceHelper,
            EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService,
            PreAuthorizationResolutionService preAuthorizationResolutionService,
            BillingEngineService billingEngineService,
            InsurancePriceListCoverageService insurancePriceListCoverageService,
            ReopenSessionAuditContext reopenSessionAuditContext
    ) {
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.procedureHelper = procedureHelper;
        this.serviceHelper = serviceHelper;
        this.encounterPreAuthorizationSyncService = encounterPreAuthorizationSyncService;
        this.preAuthorizationResolutionService = preAuthorizationResolutionService;
        this.billingEngineService = billingEngineService;
        this.insurancePriceListCoverageService = insurancePriceListCoverageService;
        this.reopenSessionAuditContext = reopenSessionAuditContext;
    }

    /**
     * Creates and bills the single line of a completed planned treatment: a PROCEDURE line when it has a
     * procedure, otherwise a SERVICE line. {@code quantity} is the billable quantity (covered-tooth count for
     * grouped treatments) and {@code toothNumber} the executed tooth of a specific-tooth treatment (null for
     * grouped ones). Caller guarantees the treatment is locked and has no charge yet.
     */
    public PatientServiceAndProduct billCompletedTreatment(
            DentalPlannedTreatment treatment,
            long quantity,
            String toothNumber
    ) {
        if (quantity <= 0) {
            throw new BadRequestAlertException(
                    "Quantity must be a positive number",
                    ENTITY,
                    "invalidQuantity"
            );
        }
        PatientEncounter executionEncounter = treatment.getExecutionEncounter();
        if (executionEncounter == null) {
            throw new BadRequestAlertException(
                    "Execution encounter is required to bill the planned treatment",
                    ENTITY,
                    "executionEncounterRequired"
            );
        }
        boolean asProcedure = treatment.getProcedureId() != null;
        BillingItemTypes itemType = asProcedure ? BillingItemTypes.PROCEDURE : BillingItemTypes.SERVICE;
        Long procedureId = asProcedure ? treatment.getProcedureId() : null;
        Long serviceId = asProcedure ? null : treatment.getServiceId();
        if (procedureId == null && serviceId == null) {
            throw new BadRequestAlertException(
                    "Dental procedure is required to complete the planned treatment",
                    ENTITY,
                    "procedureRequired"
            );
        }

        BigDecimal setupPrice;
        Currency currency;
        if (asProcedure) {
            ProcedureSetupDTO setup = procedureHelper.getProcedure(procedureId);
            setupPrice = setup.price();
            currency = setup.currency();
        } else {
            ServiceSetupDTO setup = serviceHelper.getService(serviceId);
            setupPrice = setup.price();
            currency = setup.currency();
        }
        // Provisional only: the Billing Engine resolves the real price and discount when it bills the line.
        BigDecimal unitPrice = setupPrice != null ? setupPrice : BigDecimal.ZERO;

        reopenSessionAuditContext.applyOpenSession(executionEncounter.getId());

        InsurancePriceListCoverageCheckResult coverageCheck = insurancePriceListCoverageService.check(
                executionEncounter.getId(),
                itemType,
                serviceId,
                procedureId,
                null,
                null,
                currency
        );
        // Uncovered items are billed as cash only when the request carries the existing acceptance
        // (X-Accept-Uncovered-As-Cash header or acceptUncoveredAsCash param). Otherwise this throws the
        // existing 409 and the treatment stays incomplete.
        insurancePriceListCoverageService.requireCoveredOrAcknowledged(coverageCheck, null);

        // The setup currency, else the one the coverage check resolved for this item (it defaults the same way
        // for the insurance price-list lookup). The line cannot be saved or billed without a currency.
        Currency lineCurrency = currency != null ? currency : coverageCheck.currency();
        if (lineCurrency == null) {
            throw new BadRequestAlertException(
                    "Currency is not configured for this dental procedure or service.",
                    ENTITY,
                    "currency.required"
            );
        }

        PatientServiceAndProduct.PatientServiceAndProductBuilder builder = PatientServiceAndProduct.builder()
                .patientId(treatment.getPatientId())
                .encounterId(executionEncounter.getId())
                .billingItemType(itemType)
                .procedureId(procedureId)
                .serviceId(serviceId)
                .serviceSource(ServiceSource.DENTAL_PLANNED_TREATMENT)
                .sourceId(treatment.getId())
                .toothNumber(toothNumber)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .discountAmount(BigDecimal.ZERO)
                .exemptionAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .currency(lineCurrency)
                .isBilled(Boolean.FALSE)
                .billingInvoiceId(null)
                .billingInvoiceItemId(null)
                .notes(treatment.getNote());

        if (coverageCheck.requiresCashConfirmation()) {
            insurancePriceListCoverageService.applyUncoveredCash(builder, coverageCheck);
        } else {
            preAuthorizationResolutionService.resolveAndPrepareNewItem(
                    builder,
                    executionEncounter.getId(),
                    itemType,
                    procedureId,
                    serviceId,
                    null,
                    null,
                    true,
                    lineCurrency
            );
            preAuthorizationResolutionService.enrichWaseelSbsMappingForBillingItem(
                    builder,
                    itemType,
                    procedureId,
                    serviceId,
                    null,
                    null
            );
        }

        PatientServiceAndProduct line = patientServiceAndProductRepository.saveAndFlush(builder.build());
        completeBillingFlow(executionEncounter, line);
        return line;
    }

    private void completeBillingFlow(PatientEncounter encounter, PatientServiceAndProduct item) {
        if (item.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL) {
            LOG.info(
                    "[DENTAL_PLANNED_TREATMENT] Pre-auth required — submitting to Waseel. encounterId={} pspId={}",
                    encounter.getId(),
                    item.getId()
            );
            encounterPreAuthorizationSyncService.submitPendingPreAuthorizationOrThrow(encounter.getId());
            return;
        }

        billItem(item, encounter);

        // Billing can hold a line for pre-approval (coverage contract or eligibility contract limit):
        // it stays in Products & Services and is posted to the ledger after approval.
        if (item.getPaymentStatus() == PaymentStatus.SKIPPED_PENDING_PRE_AUTH) {
            encounterPreAuthorizationSyncService.submitPendingPreAuthorizationOrThrow(encounter.getId());
        }
    }

    private void billItem(PatientServiceAndProduct item, PatientEncounter encounter) {
        Long facilityId = encounter.getFacilityId();
        if (facilityId == null) {
            throw new BadRequestAlertException(
                    "Encounter facility is required to bill dental item.",
                    ENTITY,
                    "encounter.facility.required"
            );
        }

        BillingOperationResult result = billingEngineService.onItemOrdered(
                item.getId(),
                facilityId,
                "DENTAL-PLANNED-COMPLETE:" + item.getId()
        );

        if (result == null) {
            throw new BadRequestAlertException(
                    "Dental billing returned no result.",
                    ENTITY,
                    "billing.failed"
            );
        }

        LOG.info(
                "[DENTAL_PLANNED_TREATMENT] Billed item. pspId={} type={} processed={} message={}",
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
                    ENTITY,
                    "billing.failed"
            );
        }
    }
}
