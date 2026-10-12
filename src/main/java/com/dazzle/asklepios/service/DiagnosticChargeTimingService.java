package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingRulePaymentType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingTrigger;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.service.dto.billing.BillingRuleResolveResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Uses the catalog or default billing rule to decide when a laboratory or
 * radiology charge line is created. Price calculation stays on the existing path.
 */
@Service
public class DiagnosticChargeTimingService {

    private static final Logger LOG =
            LoggerFactory.getLogger(DiagnosticChargeTimingService.class);

    private static final EnumSet<DiagnosticStatus> SAMPLE_COLLECTED_STATUSES =
            EnumSet.of(
                    DiagnosticStatus.SAMPLE_COLLECTED,
                    DiagnosticStatus.SAMPLE_REPEAT,
                    DiagnosticStatus.ACCEPTED,
                    DiagnosticStatus.EXAM_DONE,
                    DiagnosticStatus.RESULT_READY,
                    DiagnosticStatus.RESULT_APPROVED,
                    DiagnosticStatus.RESULT_REJECTED,
                    DiagnosticStatus.COMPLETED
            );

    private static final EnumSet<DiagnosticStatus> ACCEPTED_STATUSES =
            EnumSet.of(
                    DiagnosticStatus.ACCEPTED,
                    DiagnosticStatus.EXAM_DONE,
                    DiagnosticStatus.RESULT_READY,
                    DiagnosticStatus.RESULT_APPROVED,
                    DiagnosticStatus.RESULT_REJECTED,
                    DiagnosticStatus.COMPLETED
            );

    private static final EnumSet<DiagnosticStatus> APPROVED_STATUSES =
            EnumSet.of(
                    DiagnosticStatus.RESULT_APPROVED,
                    DiagnosticStatus.COMPLETED
            );

    private final SetupBillingRuleService setupBillingRuleService;
    private final CatalogBillingRuleLookupService catalogBillingRuleLookupService;
    private final EncounterCoverageService encounterCoverageService;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    public DiagnosticChargeTimingService(
            SetupBillingRuleService setupBillingRuleService,
            CatalogBillingRuleLookupService catalogBillingRuleLookupService,
            EncounterCoverageService encounterCoverageService,
            DiagnosticOrderRepository diagnosticOrderRepository,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository
    ) {
        this.setupBillingRuleService = setupBillingRuleService;
        this.catalogBillingRuleLookupService = catalogBillingRuleLookupService;
        this.encounterCoverageService = encounterCoverageService;
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
    }

    public static boolean isWorkflowTrigger(BillingTrigger trigger) {
        return trigger == BillingTrigger.SUBMIT_ORDER
                || trigger == BillingTrigger.COLLECT_SAMPLE
                || trigger == BillingTrigger.ACCEPT_ORDER
                || trigger == BillingTrigger.APPROVED;
    }

    public static boolean appliesTo(DiagnosticOrderTest test) {
        return serviceTypeOf(test) != null;
    }

    /**
     * True when the selected billing rule says this charge line is not due yet.
     * A failed setup call does not block checkout.
     */
    public boolean isChargeDeferred(PatientServiceAndProduct billingItem) {
        if (!isDiagnosticItem(billingItem)) {
            return false;
        }

        try {
            DiagnosticOrderTest test = diagnosticOrderTestRepository
                    .findById(billingItem.getSourceId())
                    .orElse(null);
            if (!appliesTo(test)) {
                return false;
            }

            DiagnosticOrder order = diagnosticOrderRepository
                    .findById(test.getOrderId())
                    .orElse(null);
            Optional<BillingRuleResolveResponse> rule =
                    controllingRule(order, test, billingItem, false);
            return rule.isPresent()
                    && !triggerReached(order, test, rule.get().billingTrigger());
        } catch (RuntimeException exception) {
            LOG.error(
                    "[BILLING_RULE] Unable to evaluate deferred charge pspId={}",
                    billingItem.getId(),
                    exception
            );
            return false;
        }
    }

    public Optional<BillingRuleResolveResponse> controllingRule(
            DiagnosticOrder order,
            DiagnosticOrderTest test,
            PatientServiceAndProduct billingItem,
            boolean required
    ) {
        if (billingItem == null || !appliesTo(test)) {
            return Optional.empty();
        }

        BillingRuleResolveResponse rule = resolveRule(billingItem, required);
        if (rule == null || !isWorkflowTrigger(rule.billingTrigger())) {
            return Optional.empty();
        }

        if (!paymentApplies(billingItem, rule)) {
            return Optional.empty();
        }

        return Optional.of(rule);
    }

    public boolean paymentApplies(
            PatientServiceAndProduct billingItem,
            BillingRuleResolveResponse rule
    ) {
        if (rule == null) {
            return false;
        }

        BillingRulePaymentType configured = rule.paymentType();
        if (configured == null || configured == BillingRulePaymentType.BOTH) {
            return true;
        }

        Long encounterId = billingItem == null ? null : billingItem.getEncounterId();
        return configured == paymentTypeOf(encounterId, billingItem);
    }

    public boolean triggerReached(
            PatientServiceAndProduct billingItem,
            BillingTrigger trigger
    ) {
        if (billingItem == null
                || billingItem.getSourceId() == null
                || !isWorkflowTrigger(trigger)) {
            return false;
        }

        DiagnosticOrderTest test = diagnosticOrderTestRepository
                .findById(billingItem.getSourceId())
                .orElse(null);
        if (test == null) {
            return false;
        }

        DiagnosticOrder order = diagnosticOrderRepository
                .findById(test.getOrderId())
                .orElse(null);
        return triggerReached(order, test, trigger);
    }

    public boolean triggerReached(
            DiagnosticOrder order,
            DiagnosticOrderTest test,
            BillingTrigger trigger
    ) {
        if (order == null || test == null || !isWorkflowTrigger(trigger)) {
            return false;
        }

        if (test.getStatus() == DiagnosticOrderTestStatus.CANCELLED
                || test.getProcessingStatus() == DiagnosticStatus.CANCELLED) {
            return false;
        }

        return switch (trigger) {
            case SUBMIT_ORDER -> isSubmitted(order, test);
            case COLLECT_SAMPLE ->
                    SAMPLE_COLLECTED_STATUSES.contains(test.getProcessingStatus());
            case ACCEPT_ORDER ->
                    ACCEPTED_STATUSES.contains(test.getProcessingStatus());
            case APPROVED ->
                    APPROVED_STATUSES.contains(test.getProcessingStatus());
            default -> false;
        };
    }

    private BillingRuleResolveResponse resolveRule(
            PatientServiceAndProduct billingItem,
            boolean required
    ) {
        try {
            Long catalogBillingRuleId =
                    catalogBillingRuleLookupService.lookupBillingRuleId(billingItem);
            return setupBillingRuleService.resolve(
                    billingItem,
                    catalogBillingRuleId
            );
        } catch (RuntimeException exception) {
            if (required) {
                throw exception;
            }

            LOG.error(
                    "[BILLING_RULE] Unable to resolve billing rule pspId={}",
                    billingItem.getId(),
                    exception
            );
            return null;
        }
    }

    private static TestType serviceTypeOf(DiagnosticOrderTest test) {
        if (test == null || test.getOrderType() == null) {
            return null;
        }

        return switch (test.getOrderType()) {
            case RADIOLOGY, LABORATORY, MICROBIOLOGY -> test.getOrderType();
            case PATHOLOGY -> null;
        };
    }

    private boolean isDiagnosticItem(PatientServiceAndProduct billingItem) {
        return billingItem != null
                && billingItem.getSourceId() != null
                && (billingItem.getServiceSource() == ServiceSource.LABORATORY
                || billingItem.getServiceSource() == ServiceSource.RADIOLOGY);
    }

    private boolean isSubmitted(DiagnosticOrder order, DiagnosticOrderTest test) {
        return Boolean.FALSE.equals(order.getSaveDraft())
                || order.getSubmittedDate() != null
                || order.getStatus() == DiagnosticStatus.SUBMITTED
                || test.getStatus() == DiagnosticOrderTestStatus.SUBMITTED;
    }

    private BillingRulePaymentType paymentTypeOf(
            Long encounterId,
            PatientServiceAndProduct billingItem
    ) {
        if (billingItem != null && billingItem.isUncoveredCashItem()) {
            return BillingRulePaymentType.SELF_PAY;
        }

        if (encounterId == null) {
            return BillingRulePaymentType.SELF_PAY;
        }

        BillingCoverageType coverageType =
                encounterCoverageService
                        .getEncounterCoverage(encounterId)
                        .coverageType();

        if (coverageType == BillingCoverageType.INSURANCE) {
            return BillingRulePaymentType.INSURANCE;
        }

        return BillingRulePaymentType.SELF_PAY;
    }
}
