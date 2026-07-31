package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.client.WaseelItemMappingClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PreAuthorizationResolutionService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PreAuthorizationResolutionService.class);

    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;

    private final WaseelItemMappingClient waseelItemMappingClient;

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
                "[PREAUTH] Insurance visit detected for encounterId={}. Checking Waseel mapping for billingItemType={}",
                encounterId,
                billingItemType
        );

        if (billingItemType == BillingItemTypes.PROCEDURE && procedureId != null) {
            return requiresPreAuthorization(billingItemType, procedureId)
                    ? Resolution.pendingApproval()
                    : Resolution.notRequired();
        }

        if (billingItemType == BillingItemTypes.SERVICE && serviceId != null) {
            return requiresPreAuthorization(billingItemType, serviceId)
                    ? Resolution.pendingApproval()
                    : Resolution.notRequired();
        }

        if ((billingItemType == BillingItemTypes.LABORATORY
                || billingItemType == BillingItemTypes.RADIOLOGY
                || billingItemType == BillingItemTypes.PATHOLOGY)
                && diagnosticTestId != null) {
            return requiresPreAuthorization(billingItemType, diagnosticTestId)
                    ? Resolution.pendingApproval()
                    : Resolution.notRequired();
        }

        if (billingItemType == BillingItemTypes.MEDICATION && brandMedicationId != null) {
            return requiresPreAuthorization(billingItemType, brandMedicationId)
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
        return item != null
                && (item.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL
                || Boolean.TRUE.equals(item.getPreAuthorizationRequired()));
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
     * Re-evaluates Waseel mapping and applies pre-authorization fields
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
                        item.getBrandMedicationId()
                );

        apply(item, resolution);
    }

    /**
     * Standard entry point when adding a billing item to an encounter:
     * 1. Link insurance context when the visit is insurance
     * 2. Query Setup/Waseel item mapping
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
        applyInsuranceVisitContext(builder, encounterId, insuranceVisitContext);

        Resolution resolution =
                resolve(
                        encounterId,
                        billingItemType,
                        procedureId,
                        serviceId,
                        diagnosticTestId,
                        brandMedicationId,
                        insuranceVisitContext
                );

        apply(builder, resolution);

        LOG.info(
                "[PREAUTH] Prepared new billing item. encounterId={} billingItemType={} "
                        + "procedureId={} serviceId={} diagnosticTestId={} brandMedicationId={} "
                        + "required={} status={}",
                encounterId,
                billingItemType,
                procedureId,
                serviceId,
                diagnosticTestId,
                brandMedicationId,
                resolution.required(),
                resolution.status()
        );

        return resolution;
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

        if (!insuranceVisitContext
                && !encounterInsuranceEligibilityService.shouldEvaluatePreAuthorization(encounterId)) {
            builder.coverageStatus(CoverageStatus.NOT_CHECKED);
            return;
        }

        builder
                .patientInsuranceId(
                        encounterInsuranceEligibilityService
                                .resolveEncounterPatientInsuranceId(encounterId)
                )
                .coverageStatus(CoverageStatus.COVERED);
    }

    private boolean requiresPreAuthorization(
            BillingItemTypes billingItemType,
            Long itemId
    ) {
        if (billingItemType == null || itemId == null) {
            return false;
        }

        LOG.info(
                "[PREAUTH] Checking Waseel item mapping. billingItemType={}, itemId={}",
                billingItemType,
                itemId
        );

        try {
            Boolean requiresPreAuth =
                    waseelItemMappingClient.requiresPreauth(
                            billingItemType,
                            itemId
                    );

            LOG.info(
                    "[PREAUTH] Waseel mapping result. billingItemType={}, itemId={}, requiresPreAuth={}",
                    billingItemType,
                    itemId,
                    requiresPreAuth
            );

            return Boolean.TRUE.equals(requiresPreAuth);
        } catch (FeignException ex) {
            LOG.error(
                    "[PREAUTH] Failed to check Waseel mapping. billingItemType={}, itemId={}, status={}, body={}",
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
