package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.client.setup.PayorPlanItemClient;
import com.dazzle.asklepios.client.setup.PriceListSetupClient;
import com.dazzle.asklepios.client.setup.dto.BillingPricingResolutionRequest;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.client.WaseelItemMappingClient;
import com.dazzle.asklepios.integration.waseel.client.dto.WaseelItemMappingSetupDTO;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.service.helper.NphiesPayerHelper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PreAuthorizationResolutionService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PreAuthorizationResolutionService.class);

    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;

    private final WaseelItemMappingClient waseelItemMappingClient;

    private final PriceListSetupClient priceListSetupClient;

    private final PayorPlanItemClient payorPlanItemClient;

    private final PatientEncounterRepository patientEncounterRepository;

    private final PatientInsuranceRepository patientInsuranceRepository;

    private final NphiesPayerHelper nphiesPayerHelper;

    public record Resolution(
            PreAuthorizationStatus status,
            boolean required
    ) {
        public static Resolution notRequired() {
            return new Resolution(PreAuthorizationStatus.NOT_REQUIRED, false);
        }

        public static Resolution pendingApproval() {
            return new Resolution(PreAuthorizationStatus.PENDING_APPROVAL, true);
        }
    }

    public Resolution resolve(
            Long encounterId,
            BillingItemTypes billingItemType,
            Long procedureId,
            Long serviceId,
            Long diagnosticTestId,
            Long brandMedicationId
    ) {
        return resolve(
                encounterId,
                billingItemType,
                procedureId,
                serviceId,
                diagnosticTestId,
                brandMedicationId,
                false
        );
    }

    public Resolution resolve(
            Long encounterId,
            BillingItemTypes billingItemType,
            Long procedureId,
            Long serviceId,
            Long diagnosticTestId,
            Long brandMedicationId,
            boolean insuranceVisitContext
    ) {
        return resolve(
                encounterId,
                billingItemType,
                procedureId,
                serviceId,
                diagnosticTestId,
                brandMedicationId,
                insuranceVisitContext,
                null
        );
    }

    public Resolution resolve(
            Long encounterId,
            BillingItemTypes billingItemType,
            Long procedureId,
            Long serviceId,
            Long diagnosticTestId,
            Long brandMedicationId,
            boolean insuranceVisitContext,
            Currency currency
    ) {
        if (encounterId == null) {
            return Resolution.notRequired();
        }

        boolean insuranceVisit = insuranceVisitContext
                || encounterInsuranceEligibilityService.shouldEvaluatePreAuthorization(encounterId);

        if (!insuranceVisit) {
            LOG.info(
                    "[PREAUTH] Skipping pre-authorization check for encounterId={} — visit is not insurance",
                    encounterId
            );
            return Resolution.notRequired();
        }

        LOG.info(
                "[PREAUTH] Insurance visit detected for encounterId={}. Checking price list item for billingItemType={}",
                encounterId,
                billingItemType
        );

        if (billingItemType == BillingItemTypes.PROCEDURE && procedureId != null) {
            return requiresPreAuthorization(
                    encounterId,
                    billingItemType,
                    procedureId,
                    currency
            )
                    ? Resolution.pendingApproval()
                    : Resolution.notRequired();
        }

        if (billingItemType == BillingItemTypes.SERVICE && serviceId != null) {
            return requiresPreAuthorization(
                    encounterId,
                    billingItemType,
                    serviceId,
                    currency
            )
                    ? Resolution.pendingApproval()
                    : Resolution.notRequired();
        }

        if ((billingItemType == BillingItemTypes.LABORATORY
                || billingItemType == BillingItemTypes.RADIOLOGY
                || billingItemType == BillingItemTypes.PATHOLOGY)
                && diagnosticTestId != null) {
            return requiresPreAuthorization(
                    encounterId,
                    billingItemType,
                    diagnosticTestId,
                    currency
            )
                    ? Resolution.pendingApproval()
                    : Resolution.notRequired();
        }

        if (billingItemType == BillingItemTypes.MEDICATION && brandMedicationId != null) {
            return requiresPreAuthorization(
                    encounterId,
                    billingItemType,
                    brandMedicationId,
                    currency
            )
                    ? Resolution.pendingApproval()
                    : Resolution.notRequired();
        }

        return Resolution.notRequired();
    }

    public void apply(PatientServiceAndProduct entity, Resolution resolution) {
        if (entity == null || resolution == null) {
            return;
        }

        entity.setPreAuthorizationStatus(resolution.status());
        entity.setPreAuthorizationRequired(resolution.required());
        entity.setPaymentStatus(
                resolution.required()
                        ? PaymentStatus.SKIPPED_PENDING_PRE_AUTH
                        : PaymentStatus.PENDING
        );
    }

    public void apply(
            PatientServiceAndProduct.PatientServiceAndProductBuilder builder,
            Resolution resolution
    ) {
        if (builder == null || resolution == null) {
            return;
        }

        builder
                .preAuthorizationStatus(resolution.status())
                .preAuthorizationRequired(resolution.required())
                .paymentStatus(
                        resolution.required()
                                ? PaymentStatus.SKIPPED_PENDING_PRE_AUTH
                                : PaymentStatus.PENDING
                );
    }

    public boolean isPendingPreAuthorization(PatientServiceAndProduct item) {
        if (item == null) {
            return false;
        }

        PreAuthorizationStatus status = item.getPreAuthorizationStatus();
        if (status == PreAuthorizationStatus.APPROVED
                || status == PreAuthorizationStatus.NOT_REQUIRED
                || status == PreAuthorizationStatus.REJECTED) {
            return false;
        }

        return status == PreAuthorizationStatus.PENDING_APPROVAL
                || Boolean.TRUE.equals(item.getPreAuthorizationRequired());
    }

    /**
     * Insurance pre-authorization items must calculate responsibilities but must
     * not post ledger debt until the authorization decision is financially valid.
     */
    public boolean shouldDeferLedgerPosting(PatientServiceAndProduct item) {
        if (item == null) {
            return false;
        }

        if (item.getPaymentStatus() == PaymentStatus.SKIPPED_PENDING_PRE_AUTH) {
            return true;
        }

        PreAuthorizationStatus status = item.getPreAuthorizationStatus();
        if (status == PreAuthorizationStatus.APPROVED
                || status == PreAuthorizationStatus.NOT_REQUIRED
                || status == PreAuthorizationStatus.REJECTED) {
            return false;
        }

        return status == PreAuthorizationStatus.PENDING_APPROVAL
                || Boolean.TRUE.equals(item.getPreAuthorizationRequired());
    }

    public PaymentStatus resolveBillingPaymentStatus(PatientServiceAndProduct item) {
        return resolveBillingPaymentStatus(item, PaymentStatus.PENDING);
    }

    public PaymentStatus resolveBillingPaymentStatus(
            PatientServiceAndProduct item,
            PaymentStatus whenNotPendingPreAuth
    ) {
        if (isPendingPreAuthorization(item)) {
            return PaymentStatus.SKIPPED_PENDING_PRE_AUTH;
        }

        return whenNotPendingPreAuth;
    }

    /**
     * Re-evaluates price list setup item and applies pre-authorization fields
     * on the billing item before responsibility / payment calculations.
     */
    public void refreshForBillingItem(PatientServiceAndProduct item) {
        if (item == null || item.getEncounterId() == null) {
            return;
        }

        Resolution resolution =
                resolve(
                        item.getEncounterId(),
                        item.getBillingItemType(),
                        item.getProcedureId(),
                        item.getServiceId(),
                        item.getDiagnosticTestId(),
                        item.getBrandMedicationId(),
                        false,
                        item.getCurrency()
                );

        apply(item, resolution);
    }

    /**
     * Standard entry point when adding a billing item to an encounter:
     * 1. Link insurance context when the visit is insurance
     * 2. Query Setup price list item (type + payor)
     * 3. Apply pre-authorization + payment status on the builder
     */
    public Resolution resolveAndPrepareNewItem(
            PatientServiceAndProduct.PatientServiceAndProductBuilder builder,
            Long encounterId,
            BillingItemTypes billingItemType,
            Long procedureId,
            Long serviceId,
            Long diagnosticTestId,
            Long brandMedicationId
    ) {
        return resolveAndPrepareNewItem(
                builder,
                encounterId,
                billingItemType,
                procedureId,
                serviceId,
                diagnosticTestId,
                brandMedicationId,
                false
        );
    }

    public Resolution resolveAndPrepareNewItem(
            PatientServiceAndProduct.PatientServiceAndProductBuilder builder,
            Long encounterId,
            BillingItemTypes billingItemType,
            Long procedureId,
            Long serviceId,
            Long diagnosticTestId,
            Long brandMedicationId,
            boolean insuranceVisitContext
    ) {
        return resolveAndPrepareNewItem(
                builder,
                encounterId,
                billingItemType,
                procedureId,
                serviceId,
                diagnosticTestId,
                brandMedicationId,
                insuranceVisitContext,
                null
        );
    }

    public Resolution resolveAndPrepareNewItem(
            PatientServiceAndProduct.PatientServiceAndProductBuilder builder,
            Long encounterId,
            BillingItemTypes billingItemType,
            Long procedureId,
            Long serviceId,
            Long diagnosticTestId,
            Long brandMedicationId,
            boolean insuranceVisitContext,
            Currency currency
    ) {
        applyInsuranceVisitContext(builder, encounterId, insuranceVisitContext);

        Resolution resolution =
                resolve(
                        encounterId,
                        billingItemType,
                        procedureId,
                        serviceId,
                        diagnosticTestId,
                        brandMedicationId,
                        insuranceVisitContext,
                        currency
                );

        apply(builder, resolution);

        LOG.info(
                "[PREAUTH] Prepared new billing item. encounterId={} billingItemType={} "
                        + "procedureId={} serviceId={} diagnosticTestId={} brandMedicationId={} "
                        + "currency={} required={} status={}",
                encounterId,
                billingItemType,
                procedureId,
                serviceId,
                diagnosticTestId,
                brandMedicationId,
                currency,
                resolution.required(),
                resolution.status()
        );

        return resolution;
    }

    /**
     * Attaches Waseel SBS mapping metadata when the visit is insurance.
     */
    public void enrichWaseelSbsMapping(
            PatientServiceAndProduct.PatientServiceAndProductBuilder builder,
            BillingItemTypes billingItemType,
            Long sourceId
    ) {
        if (builder == null || billingItemType == null || sourceId == null) {
            return;
        }

        try {
            WaseelItemMappingSetupDTO mapping =
                    waseelItemMappingClient.getMappingByItem(
                            billingItemType.name(),
                            sourceId
                    );

            if (mapping == null) {
                return;
            }

            builder
                    .waseelSbsMappingId(mapping.id())
                    .waseelSbsCode(mapping.sbsCode());

            LOG.info(
                    "[PREAUTH] Applied SBS mapping. billingItemType={} sourceId={} sbsCode={}",
                    billingItemType,
                    sourceId,
                    mapping.sbsCode()
            );
        } catch (FeignException ex) {
            LOG.warn(
                    "[PREAUTH] Unable to load SBS mapping. billingItemType={} sourceId={} status={}",
                    billingItemType,
                    sourceId,
                    ex.status(),
                    ex
            );
        }
    }

    /**
     * Resolves and attaches Waseel SBS mapping for any supported billing item type.
     */
    public void enrichWaseelSbsMappingForBillingItem(
            PatientServiceAndProduct.PatientServiceAndProductBuilder builder,
            BillingItemTypes billingItemType,
            Long procedureId,
            Long serviceId,
            Long diagnosticTestId,
            Long brandMedicationId
    ) {
        Long sourceId =
                resolveCatalogSourceId(
                        billingItemType,
                        procedureId,
                        serviceId,
                        diagnosticTestId,
                        brandMedicationId
                );

        if (sourceId == null) {
            return;
        }

        enrichWaseelSbsMapping(
                builder,
                billingItemType,
                sourceId
        );
    }

    public void applyInsuranceVisitContext(
            PatientServiceAndProduct.PatientServiceAndProductBuilder builder,
            Long encounterId
    ) {
        applyInsuranceVisitContext(builder, encounterId, false);
    }

    public void applyInsuranceVisitContext(
            PatientServiceAndProduct.PatientServiceAndProductBuilder builder,
            Long encounterId,
            boolean insuranceVisitContext
    ) {
        if (builder == null || encounterId == null) {
            return;
        }

        if (!isInsuranceEncounter(encounterId, insuranceVisitContext)) {
            builder.coverageStatus(CoverageStatus.NOT_CHECKED);
            return;
        }

        applyInsuranceFields(builder, encounterId);
    }

    /**
     * Links encounter insurance context to an existing billing item.
     * Used when insurance is selected after items were already ordered.
     */
    public void applyEncounterInsuranceLink(
            PatientServiceAndProduct item,
            Long encounterId
    ) {
        applyEncounterInsuranceLink(item, encounterId, false);
    }

    public void applyEncounterInsuranceLink(
            PatientServiceAndProduct item,
            Long encounterId,
            boolean insuranceVisitContext
    ) {
        if (item == null || encounterId == null) {
            return;
        }

        if (!isInsuranceEncounter(encounterId, insuranceVisitContext)) {
            return;
        }

        Long insuranceId =
                encounterInsuranceEligibilityService.resolveEncounterPatientInsuranceId(encounterId);

        if (insuranceId != null) {
            item.setPatientInsuranceId(insuranceId);
            item.setCoverageStatus(CoverageStatus.COVERED);
        }
    }

    public BillingCoverageType resolveEncounterCoverageType(Long encounterId) {
        return isInsuranceEncounter(encounterId, false)
                ? BillingCoverageType.INSURANCE
                : BillingCoverageType.SELF_PAY;
    }

    private boolean isInsuranceEncounter(Long encounterId, boolean insuranceVisitContext) {
        return insuranceVisitContext
                || encounterInsuranceEligibilityService.shouldEvaluatePreAuthorization(encounterId);
    }

    private void applyInsuranceFields(
            PatientServiceAndProduct.PatientServiceAndProductBuilder builder,
            Long encounterId
    ) {
        builder
                .patientInsuranceId(
                        encounterInsuranceEligibilityService
                                .resolveEncounterPatientInsuranceId(encounterId)
                )
                .coverageStatus(CoverageStatus.COVERED);
    }

    private boolean requiresPreAuthorization(
            Long encounterId,
            BillingItemTypes billingItemType,
            Long itemId,
            Currency currency
    ) {
        if (encounterId == null
                || billingItemType == null
                || itemId == null) {
            return false;
        }

        if (requiresPreAuthorizationFromPriceList(
                encounterId,
                billingItemType,
                itemId,
                currency
        )) {
            return true;
        }

        return requiresPreAuthorizationFromPayorPlan(
                billingItemType,
                itemId
        );
    }

    private boolean requiresPreAuthorizationFromPriceList(
            Long encounterId,
            BillingItemTypes billingItemType,
            Long itemId,
            Currency currency
    ) {
        Optional<BillingPricingResolutionRequest> requestOptional =
                buildPriceListPreauthRequest(
                        encounterId,
                        billingItemType,
                        itemId,
                        currency
                );

        if (requestOptional.isEmpty()) {
            LOG.info(
                    "[PREAUTH] Unable to build price list pre-auth request. encounterId={} billingItemType={} itemId={}",
                    encounterId,
                    billingItemType,
                    itemId
            );
            return false;
        }

        BillingPricingResolutionRequest request =
                requestOptional.get();

        LOG.info(
                "[PREAUTH] Checking insurance price list item. encounterId={} billingItemType={} itemId={} payerId={}",
                encounterId,
                billingItemType,
                itemId,
                request.payerId()
        );

        try {
            Boolean requiresPreAuth =
                    priceListSetupClient.requiresPreAuthorization(
                            request
                    );

            LOG.info(
                    "[PREAUTH] Price list result. encounterId={} billingItemType={} itemId={} requiresPreAuth={}",
                    encounterId,
                    billingItemType,
                    itemId,
                    requiresPreAuth
            );

            return Boolean.TRUE.equals(requiresPreAuth);
        } catch (FeignException ex) {
            LOG.error(
                    "[PREAUTH] Failed to check price list pre-authorization. encounterId={} billingItemType={} itemId={} status={} body={}",
                    encounterId,
                    billingItemType,
                    itemId,
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );

            return false;
        }
    }

    private Optional<BillingPricingResolutionRequest> buildPriceListPreauthRequest(
            Long encounterId,
            BillingItemTypes billingItemType,
            Long itemId,
            Currency currency
    ) {
        Optional<PatientEncounter> encounterOptional =
                patientEncounterRepository.findById(encounterId);

        if (encounterOptional.isEmpty()) {
            return Optional.empty();
        }

        PatientEncounter encounter =
                encounterOptional.get();

        Long patientInsuranceId =
                encounterInsuranceEligibilityService
                        .resolveEncounterPatientInsuranceId(
                                encounterId
                        );

        Long payerId =
                resolvePriceListPayerId(
                        patientInsuranceId
                );

        return Optional.of(
                new BillingPricingResolutionRequest(
                        encounter.getFacilityId(),
                        encounter.getPatient().getId(),
                        encounterId,
                        billingItemType,
                        itemId,
                        patientInsuranceId,
                        payerId,
                        BillingCoverageType.INSURANCE,
                        currency != null
                                ? currency
                                : Currency.SAR,
                        LocalDate.now()
                )
        );
    }

    private Long resolveCatalogSourceId(
            BillingItemTypes billingItemType,
            Long procedureId,
            Long serviceId,
            Long diagnosticTestId,
            Long brandMedicationId
    ) {
        return switch (billingItemType) {
            case PROCEDURE -> procedureId;
            case SERVICE -> serviceId;
            case LABORATORY, RADIOLOGY, PATHOLOGY -> diagnosticTestId;
            case MEDICATION -> brandMedicationId;
        };
    }

    private Long resolvePriceListPayerId(
            Long patientInsuranceId
    ) {
        if (patientInsuranceId == null) {
            return null;
        }

        return patientInsuranceRepository
                .findById(patientInsuranceId)
                .map(this::resolvePriceListPayerId)
                .orElse(null);
    }

    private Long resolvePriceListPayerId(
            PatientInsurance insurance
    ) {
        return nphiesPayerHelper.resolvePriceListPayerId(
                insurance.getPayorId(),
                insurance.getPayerNphiesId()
        );
    }

    private boolean requiresPreAuthorizationFromPayorPlan(
            BillingItemTypes billingItemType,
            Long itemId
    ) {
        LOG.info(
                "[PREAUTH] Checking payor plan item mapping. billingItemType={}, itemId={}",
                billingItemType,
                itemId
        );

        try {
            Boolean requiresPreAuth =
                    switch (billingItemType) {
                        case PROCEDURE ->
                                payorPlanItemClient.requiresPreAuthorizationForProcedure(itemId);
                        case SERVICE ->
                                payorPlanItemClient.requiresPreAuthorizationForService(itemId);
                        case LABORATORY, RADIOLOGY, PATHOLOGY ->
                                payorPlanItemClient.requiresPreAuthorizationForDiagnosticTest(itemId);
                        case MEDICATION ->
                                payorPlanItemClient.requiresPreAuthorizationForMedication(itemId);
                    };

            LOG.info(
                    "[PREAUTH] Payor plan result. billingItemType={}, itemId={}, requiresPreAuth={}",
                    billingItemType,
                    itemId,
                    requiresPreAuth
            );

            return Boolean.TRUE.equals(requiresPreAuth);
        } catch (FeignException ex) {
            LOG.error(
                    "[PREAUTH] Failed to check payor plan pre-authorization. billingItemType={}, itemId={}, status={}, body={}",
                    billingItemType,
                    itemId,
                    ex.status(),
                    ex.contentUTF8(),
                    ex
            );

            return false;
        }
    }
}
