package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.integration.waseel.event.EligibilityCheckSucceededEvent;
import com.dazzle.asklepios.integration.waseel.event.EncounterPreAuthorizationSyncEvent;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumSet;
import java.util.List;

@Service
public class EncounterPreAuthorizationSyncService {

    private static final Logger LOG =
            LoggerFactory.getLogger(EncounterPreAuthorizationSyncService.class);

    private static final EnumSet<PreAuthorizationStatus> FINAL_PRE_AUTHORIZATION_STATUSES =
            EnumSet.of(PreAuthorizationStatus.APPROVED, PreAuthorizationStatus.REJECTED);

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;
    private final PreAuthorizationResolutionService preAuthorizationResolutionService;
    private final PreAuthorizationSubmissionService preAuthorizationSubmissionService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EncounterPreAuthorizationSyncService self;

    public EncounterPreAuthorizationSyncService(
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            EncounterInsuranceEligibilityService encounterInsuranceEligibilityService,
            PreAuthorizationResolutionService preAuthorizationResolutionService,
            PreAuthorizationSubmissionService preAuthorizationSubmissionService,
            ApplicationEventPublisher applicationEventPublisher,
            @Lazy EncounterPreAuthorizationSyncService self
    ) {
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.encounterInsuranceEligibilityService = encounterInsuranceEligibilityService;
        this.preAuthorizationResolutionService = preAuthorizationResolutionService;
        this.preAuthorizationSubmissionService = preAuthorizationSubmissionService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.self = self;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncEncounter(Long encounterId) {
        doSyncEncounter(encounterId, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncEncounter(Long encounterId, BillingCoverageType coverageType) {
        doSyncEncounter(encounterId, coverageType);
    }

    private void doSyncEncounter(Long encounterId, BillingCoverageType coverageType) {
        if (encounterId == null) {
            return;
        }

        if (!encounterInsuranceEligibilityService.shouldEvaluatePreAuthorization(
                encounterId,
                coverageType
        )) {
            LOG.info(
                    "[PREAUTH_SYNC] Skipping encounterId={} — visit is not insurance",
                    encounterId
            );
            return;
        }

        boolean insuranceVisitContext =
                coverageType == BillingCoverageType.INSURANCE
                        || encounterInsuranceEligibilityService.shouldEvaluatePreAuthorization(
                                encounterId
                        );

        List<PatientServiceAndProduct> items =
                patientServiceAndProductRepository.findByEncounterId(encounterId);

        if (items == null || items.isEmpty()) {
            LOG.debug("[PREAUTH_SYNC] No billing items found for encounterId={}", encounterId);
            return;
        }

        boolean hasPendingItems = false;

        for (PatientServiceAndProduct item : items) {
            if (Boolean.TRUE.equals(item.getIsBilled())) {
                continue;
            }

            if (item.isUncoveredCashItem()) {
                continue;
            }

            if (item.getPreAuthorizationStatus() != null
                    && FINAL_PRE_AUTHORIZATION_STATUSES.contains(item.getPreAuthorizationStatus())) {
                continue;
            }

            PreAuthorizationResolutionService.Resolution resolution =
                    preAuthorizationResolutionService.resolve(
                            encounterId,
                            item.getBillingItemType(),
                            item.getProcedureId(),
                            item.getServiceId(),
                            item.getDiagnosticTestId(),
                            item.getBrandMedicationId(),
                            insuranceVisitContext,
                            item.getCurrency()
                    );

            preAuthorizationResolutionService.apply(item, resolution);
            preAuthorizationResolutionService.applyEncounterInsuranceLink(
                    item,
                    encounterId,
                    insuranceVisitContext
            );

            if (resolution.required()) {
                hasPendingItems = true;

                LOG.info(
                        "[PREAUTH_SYNC] Item requires pre-authorization. encounterId={}, itemId={}, billingItemType={}, brandMedicationId={}, serviceId={}, procedureId={}, diagnosticTestId={}",
                        encounterId,
                        item.getId(),
                        item.getBillingItemType(),
                        item.getBrandMedicationId(),
                        item.getServiceId(),
                        item.getProcedureId(),
                        item.getDiagnosticTestId()
                );
            }
        }

        patientServiceAndProductRepository.saveAll(items);
        patientServiceAndProductRepository.flush();

        if (!hasPendingItems) {
            LOG.debug("[PREAUTH_SYNC] No pending pre-authorization items for encounterId={}", encounterId);
            return;
        }

        submitPendingPreAuthorization(encounterId);
    }

    public void submitPendingPreAuthorization(Long encounterId) {
        if (encounterId == null) {
            return;
        }

        LOG.info(
                "[PREAUTH_SYNC] Backend auto-submit triggered for encounterId={}",
                encounterId
        );

        try {
            preAuthorizationSubmissionService.submitIfRequired(encounterId);
        } catch (RuntimeException ex) {
            LOG.warn(
                    "[PREAUTH_SYNC] Pre-authorization submission failed for encounterId={}. Items remain pending. reason={}",
                    encounterId,
                    ex.getMessage(),
                    ex
            );
        }
    }

    /**
     * Submits pending pre-authorization synchronously within the caller transaction.
     * Propagates failures so the caller can roll back the ordered item.
     */
    public void submitPendingPreAuthorizationOrThrow(Long encounterId) {
        if (encounterId == null) {
            return;
        }

        LOG.info(
                "[PREAUTH_SYNC] Synchronous pre-authorization submit for encounterId={}",
                encounterId
        );

        preAuthorizationSubmissionService.submitIfRequiredJoiningTransaction(encounterId);
    }

    /**
     * Schedules sync/submit after commit so pending PSP rows are visible
     * to the REQUIRES_NEW pre-authorization submission transaction.
     */
    public void afterItemPersisted(Long encounterId) {
        scheduleSyncAfterCommit(encounterId);
    }

    public void scheduleSyncAfterCommit(Long encounterId) {
        scheduleSyncAfterCommit(encounterId, null);
    }

    public void scheduleSyncAfterCommit(
            Long encounterId,
            BillingCoverageType coverageType
    ) {
        if (encounterId == null) {
            return;
        }

        LOG.info(
                "[PREAUTH_SYNC] Scheduling backend sync after commit for encounterId={}",
                encounterId
        );

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            applicationEventPublisher.publishEvent(
                    new EncounterPreAuthorizationSyncEvent(encounterId, coverageType)
            );
            return;
        }

        self.syncEncounter(encounterId, coverageType);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEncounterPreAuthorizationSync(EncounterPreAuthorizationSyncEvent event) {
        if (event == null || event.encounterId() == null) {
            return;
        }

        LOG.info(
                "[PREAUTH_SYNC] Running post-commit sync for encounterId={}",
                event.encounterId()
        );

        self.syncEncounter(event.encounterId(), event.coverageType());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEligibilityCheckSucceeded(EligibilityCheckSucceededEvent event) {
        if (event == null || event.encounterId() == null) {
            return;
        }

        self.syncEncounter(event.encounterId());
    }
}
