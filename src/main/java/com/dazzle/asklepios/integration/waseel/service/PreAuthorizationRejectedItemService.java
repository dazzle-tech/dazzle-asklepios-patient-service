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
import com.dazzle.asklepios.service.DiagnosticPreAuthorizationService;
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
    private final DiagnosticPreAuthorizationService diagnosticPreAuthorizationService;

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

        return billPatientChoice(
                encounterId,
                item,
                "PREAUTH-REJECTED-CASH:",
                "Rejected pre-authorization item repriced as patient cash."
        );
    }

    /**
     * The patient accepts a Waseel partial approval: insurance pays the approved amount and the
     * patient pays the difference. The other choice, cancelling the service, uses the normal cancel.
     */
    @Transactional
    public BillingOperationResult acceptPartialApproval(
            Long encounterId,
            Long patientServiceProductId
    ) {
        PatientServiceAndProduct item = requireItemOfEncounter(encounterId, patientServiceProductId);

        if (item.getPreAuthorizationStatus() != PreAuthorizationStatus.PARTIAL
                || !Boolean.TRUE.equals(item.getPreAuthorizationRequired())) {
            throw new BadRequestAlertException(
                    "Only a partial approval waiting for the patient's choice can be accepted.",
                    "preAuthorization",
                    "item.notPartial"
            );
        }

        return billPatientChoice(
                encounterId,
                item,
                "PREAUTH-PARTIAL-ACCEPTED:",
                "Partial approval accepted: insurance pays the approved amount, the patient the rest."
        );
    }

    /**
     * Releases the line from the pre-authorization hold and bills it. The billing split reads the
     * decision: a rejected line is all patient, an accepted partial is capped at the approved amount.
     */
    private BillingOperationResult billPatientChoice(
            Long encounterId,
            PatientServiceAndProduct item,
            String requestTagPrefix,
            String repricedMessage
    ) {
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
                                    repricedMessage
                            );
                        })
                        .orElseGet(() ->
                                billingEngineService.onItemOrdered(
                                        item.getId(),
                                        facilityId,
                                        requestTagPrefix + item.getId()
                                )
                        );

        LOG.info(
                "[PREAUTH_PATIENT_CHOICE] {} encounterId={} pspId={} processed={} chargeLineId={}",
                requestTagPrefix,
                encounterId,
                item.getId(),
                result.processed(),
                result.chargeLineId()
        );

        if (!result.processed()) {
            throw new BadRequestAlertException(
                    result.message() == null
                            ? "Billing did not start for the pre-authorization item."
                            : result.message(),
                    "preAuthorization",
                    "billing.rejectedCash.failed"
            );
        }

        diagnosticPreAuthorizationService.releaseHeldTest(item);

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

    private PatientServiceAndProduct requireItemOfEncounter(
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

        return item;
    }

    private PatientServiceAndProduct requireRejectedItem(
            Long encounterId,
            Long patientServiceProductId
    ) {
        PatientServiceAndProduct item = requireItemOfEncounter(encounterId, patientServiceProductId);

        if (!PreAuthorizationResolutionService.isDeclinedByPayer(item)) {
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
