package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.service.BillingChargeService;
import com.dazzle.asklepios.service.BillingEngineService;
import com.dazzle.asklepios.service.BillingResponsibilityService;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreAuthorizationRejectedItemService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PreAuthorizationRejectedItemService.class);

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final BillingChargeService billingChargeService;
    private final BillingChargeLineRepository billingChargeLineRepository;
    private final BillingEngineService billingEngineService;
    private final BillingResponsibilityService billingResponsibilityService;
    private final EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService;

    /**
     * Bills a rejected pre-authorization item with insurance price-list pricing
     * and 100% patient responsibility (full cash payment).
     */
    @Transactional
    public BillingOperationResult payRejectedItemAsCash(
            Long encounterId,
            Long patientServiceProductId
    ) {
        PatientServiceAndProduct item =
                requireRejectedItem(encounterId, patientServiceProductId);

        if (Boolean.TRUE.equals(item.getIsBilled())) {
            throw new BadRequestAlertException(
                    "Item is already billed.",
                    "preAuthorization",
                    "item.alreadyBilled"
            );
        }

        item.setPreAuthorizationRequired(false);
        item.setPaymentStatus(PaymentStatus.PENDING);
        patientServiceAndProductRepository.saveAndFlush(item);

        Long facilityId = requireFacilityId(encounterId);

        BillingOperationResult result =
                billingChargeService
                        .findActiveChargeLine(
                                item.getId(),
                                encounterId
                        )
                        .map(existingLine -> {
                            billingResponsibilityService
                                    .refreshInsuranceResponsibilitiesForEncounter(
                                            encounterId
                                    );

                            BillingChargeLine chargeLine =
                                    billingChargeLineRepository
                                            .findById(existingLine.getId())
                                            .orElse(existingLine);

                            return new BillingOperationResult(
                                    item.getId(),
                                    chargeLine.getCharge().getId(),
                                    chargeLine.getId(),
                                    null,
                                    chargeLine.getGrossAmount(),
                                    chargeLine.getDiscountAmount(),
                                    chargeLine.getExemptionAmount(),
                                    chargeLine.getTaxAmount(),
                                    chargeLine.getNetAmount(),
                                    chargeLine.getPatientResponsibilityAmount(),
                                    chargeLine.getInsuranceResponsibilityAmount(),
                                    chargeLine.getReservedAmount(),
                                    true,
                                    "Rejected pre-authorization item repriced as patient cash."
                            );
                        })
                        .orElseGet(() ->
                                billingEngineService.onItemOrdered(
                                        item.getId(),
                                        facilityId,
                                        "PREAUTH-REJECTED-CASH:" + item.getId()
                                )
                        );

        LOG.info(
                "[PREAUTH_REJECTED_CASH] encounterId={} pspId={} processed={} chargeLineId={}",
                encounterId,
                item.getId(),
                result.processed(),
                result.chargeLineId()
        );

        if (!result.processed()) {
            throw new BadRequestAlertException(
                    result.message() == null
                            ? "Billing did not start for rejected pre-authorization item."
                            : result.message(),
                    "preAuthorization",
                    "billing.rejectedCash.failed"
            );
        }

        return result;
    }

    /**
     * Resets a rejected item to pending approval and submits a new Waseel request.
     */
    @Transactional
    public void clonePreAuthorization(
            Long encounterId,
            Long patientServiceProductId
    ) {
        PatientServiceAndProduct item =
                requireRejectedItem(encounterId, patientServiceProductId);

        if (Boolean.TRUE.equals(item.getIsBilled())) {
            throw new BadRequestAlertException(
                    "Item is already billed.",
                    "preAuthorization",
                    "item.alreadyBilled"
            );
        }

        item.setPreAuthorizationStatus(PreAuthorizationStatus.PENDING_APPROVAL);
        item.setPreAuthorizationRequired(true);
        item.setPaymentStatus(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
        item.setPreAuthorizationRequestId(null);
        item.setPreAuthorizationReferenceNo(null);
        patientServiceAndProductRepository.saveAndFlush(item);

        LOG.info(
                "[PREAUTH_CLONE] Reset rejected item for new submission. encounterId={} pspId={}",
                encounterId,
                item.getId()
        );

        encounterPreAuthorizationSyncService.submitPendingPreAuthorizationOrThrow(encounterId);
    }

    private PatientServiceAndProduct requireRejectedItem(
            Long encounterId,
            Long patientServiceProductId
    ) {
        PatientServiceAndProduct item =
                patientServiceAndProductRepository.findById(patientServiceProductId)
                        .orElseThrow(() -> new BadRequestAlertException(
                                "Billing item not found.",
                                "preAuthorization",
                                "item.notFound"
                        ));

        if (!encounterId.equals(item.getEncounterId())) {
            throw new BadRequestAlertException(
                    "Billing item does not belong to this encounter.",
                    "preAuthorization",
                    "item.encounterMismatch"
            );
        }

        if (item.getPreAuthorizationStatus() != PreAuthorizationStatus.REJECTED) {
            throw new BadRequestAlertException(
                    "Only rejected pre-authorization items can use this action.",
                    "preAuthorization",
                    "item.notRejected"
            );
        }

        return item;
    }

    private Long requireFacilityId(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .map(PatientEncounter::getFacilityId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found.",
                        "preAuthorization",
                        "encounter.notFound"
                ));
    }
}
