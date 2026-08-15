package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.event.PreAuthorizationApprovedEvent;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.BillingChargeService;
import com.dazzle.asklepios.service.BillingEngineService;
import com.dazzle.asklepios.service.PatientItemPricingApplicationService;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumSet;
import java.util.List;

@Service
public class PreAuthorizationCompletionService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PreAuthorizationCompletionService.class);

    private static final EnumSet<BillingItemTypes> DEFERRED_BILLING_ITEM_TYPES =
            EnumSet.of(
                    BillingItemTypes.MEDICATION,
                    BillingItemTypes.LABORATORY,
                    BillingItemTypes.RADIOLOGY,
                    BillingItemTypes.PATHOLOGY,
                    BillingItemTypes.SERVICE,
                    BillingItemTypes.PROCEDURE
            );

    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final PatientProcedureRepository patientProcedureRepository;
    private final BillingChargeService billingChargeService;
    private final BillingEngineService billingEngineService;
    private final EncounterInsuranceResponsibilityRefreshService
            encounterInsuranceResponsibilityRefreshService;
    private final PatientItemPricingApplicationService patientItemPricingApplicationService;
    private final PreAuthorizationCompletionService self;

    public PreAuthorizationCompletionService(
            PatientEncounterRepository patientEncounterRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            PatientProcedureRepository patientProcedureRepository,
            BillingChargeService billingChargeService,
            BillingEngineService billingEngineService,
            EncounterInsuranceResponsibilityRefreshService
                    encounterInsuranceResponsibilityRefreshService,
            PatientItemPricingApplicationService patientItemPricingApplicationService,
            @Lazy PreAuthorizationCompletionService self
    ) {
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.patientProcedureRepository = patientProcedureRepository;
        this.billingChargeService = billingChargeService;
        this.billingEngineService = billingEngineService;
        this.encounterInsuranceResponsibilityRefreshService =
                encounterInsuranceResponsibilityRefreshService;
        this.patientItemPricingApplicationService = patientItemPricingApplicationService;
        this.self = self;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPreAuthorizationApproved(PreAuthorizationApprovedEvent event) {
        if (event == null
                || event.encounterId() == null
                || event.patientServiceProductIds() == null
                || event.patientServiceProductIds().isEmpty()) {
            return;
        }

        List<PatientServiceAndProduct> approvedItems =
                patientServiceAndProductRepository.findAllById(event.patientServiceProductIds());

        self.completeApprovedItems(event.encounterId(), approvedItems);
    }

    @Transactional
    public void completeApprovedItems(
            Long encounterId,
            List<PatientServiceAndProduct> approvedItems
    ) {
        if (encounterId == null || approvedItems == null || approvedItems.isEmpty()) {
            return;
        }

        Long facilityId =
                patientEncounterRepository
                        .findById(encounterId)
                        .map(PatientEncounter::getFacilityId)
                        .orElse(null);

        if (facilityId == null) {
            LOG.warn(
                    "[PREAUTH_COMPLETE] Missing facility for encounterId={} — skipping deferred billing",
                    encounterId
            );
            return;
        }

        for (PatientServiceAndProduct item : approvedItems) {
            releaseProcedureAfterPreAuthorization(item);

            if (!shouldBillDeferredItem(item, encounterId)) {
                continue;
            }

            item.setPaymentStatus(PaymentStatus.PENDING);

            BillingOperationResult result =
                    billingEngineService.onItemOrdered(
                            item.getId(),
                            facilityId,
                            "PREAUTH-APPROVED:" + item.getId()
                    );

            LOG.info(
                    "[PREAUTH_COMPLETE] encounterId={} pspId={} billingItemType={} processed={} chargeLineId={} message={}",
                    encounterId,
                    item.getId(),
                    item.getBillingItemType(),
                    result.processed(),
                    result.chargeLineId(),
                    result.message()
            );

            if (!result.processed()) {
                throw new BadRequestAlertException(
                        result.message() == null
                                ? "Billing did not start after pre-authorization approval."
                                : result.message(),
                        "preAuthorization",
                        "billing.afterApproval.failed"
                );
            }
        }

        int repricedItems =
                patientItemPricingApplicationService.reapplyInsurancePlanForEncounter(
                        encounterId,
                        facilityId
                );

        int refreshedLines =
                encounterInsuranceResponsibilityRefreshService.refreshEncounter(encounterId);

        LOG.info(
                "[PREAUTH_COMPLETE] Refreshed billing responsibilities after approval billing "
                        + "encounterId={} repricedItems={} refreshedLines={}",
                encounterId,
                repricedItems,
                refreshedLines
        );
    }

    private void releaseProcedureAfterPreAuthorization(PatientServiceAndProduct item) {
        if (item == null
                || item.getBillingItemType() != BillingItemTypes.PROCEDURE
                || item.getServiceSource() != ServiceSource.PROCEDURE
                || item.getSourceId() == null) {
            return;
        }

        patientProcedureRepository.findById(item.getSourceId()).ifPresent(procedure -> {
            if (procedure.getStatus() != ProcStatus.WAITING_PRE_AUTHORIZATION) {
                return;
            }

            procedure.setStatus(ProcStatus.REQUESTED);
            patientProcedureRepository.save(procedure);

            LOG.info(
                    "[PREAUTH_COMPLETE] Procedure released after approval. procedureId={} status={}",
                    procedure.getId(),
                    procedure.getStatus()
            );
        });
    }

    private boolean shouldBillDeferredItem(
            PatientServiceAndProduct item,
            Long encounterId
    ) {
        if (item == null
                || item.getId() == null
                || Boolean.TRUE.equals(item.getIsBilled())) {
            return false;
        }

        if (item.getPreAuthorizationStatus() != PreAuthorizationStatus.APPROVED) {
            return false;
        }

        if (!DEFERRED_BILLING_ITEM_TYPES.contains(item.getBillingItemType())) {
            return false;
        }

        return billingChargeService
                .findActiveChargeLine(item.getId(), encounterId)
                .isEmpty();
    }
}
