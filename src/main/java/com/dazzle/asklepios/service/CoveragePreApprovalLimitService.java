package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.CoverageContractResolveDtos;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationItem;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.EncounterInsuranceEligibilityService;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PreAuthorizationItemRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.service.dto.billing.BillingPricingInput;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Opens a local pre-approval request when a visit line needs coverage pre-approval.
 * No payer API is called; the charge stays off the patient ledger until an approval
 * decision exists.
 *
 * Every contract: a line that crosses the contract pre-approval limit is held.
 * NPHIES contracts (local approval flow) also hold a line when the contract
 * pre-approval has no limit, so it never waits for a Waseel submission.
 */
@Service
public class CoveragePreApprovalLimitService {

    static final String REQUEST_STATUS = "Pending Approval";
    static final String REASON_LIMIT = "Coverage pre-approval limit exceeded";
    static final String REASON_CONTRACT = "Coverage contract requires pre-approval";

    private static final Logger LOG = LoggerFactory.getLogger(CoveragePreApprovalLimitService.class);
    private static final int MONEY_SCALE = 2;

    private final CoverageContractShareService coverageContractShareService;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final PreAuthorizationItemRepository preAuthorizationItemRepository;
    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;

    public CoveragePreApprovalLimitService(
            CoverageContractShareService coverageContractShareService,
            PatientEncounterRepository patientEncounterRepository,
            PatientInsuranceRepository patientInsuranceRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            PreAuthorizationRequestRepository preAuthorizationRequestRepository,
            PreAuthorizationItemRepository preAuthorizationItemRepository,
            EncounterInsuranceEligibilityService encounterInsuranceEligibilityService
    ) {
        this.coverageContractShareService = coverageContractShareService;
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.preAuthorizationRequestRepository = preAuthorizationRequestRepository;
        this.preAuthorizationItemRepository = preAuthorizationItemRepository;
        this.encounterInsuranceEligibilityService = encounterInsuranceEligibilityService;
    }

    public void applyIfExceeded(BillingProcessingContext context) {
        if (context == null || context.getPatientServiceProduct() == null) {
            return;
        }
        PatientServiceAndProduct item = context.getPatientServiceProduct();
        if (!canEvaluate(item)) {
            return;
        }

        BigDecimal current = money(context.getNetAmount());

        PatientEncounter encounter = patientEncounterRepository.findById(item.getEncounterId()).orElse(null);
        if (encounter == null || encounter.getPatient() == null) {
            return;
        }

        Long insuranceId = item.getPatientInsuranceId() != null
                ? item.getPatientInsuranceId()
                : encounter.getPatientInsuranceId();
        if (insuranceId == null) {
            return;
        }
        PatientInsurance insurance = patientInsuranceRepository.findById(insuranceId).orElse(null);
        if (insurance == null) {
            return;
        }

        CoverageContractResolveDtos.Response resolved = resolveQuietly(insurance, item);
        boolean alreadyPending = item.getPreAuthorizationStatus() == PreAuthorizationStatus.PENDING_APPROVAL;
        if (alreadyPending && !CoverageApprovalRoutingService.usesLocalApproval(resolved)) {
            // Waseel contract: the Waseel flow submits this item. Unchanged.
            return;
        }

        String reason = holdReason(resolved, current);
        if (reason == null) {
            LOG.info(
                    "[PREAUTH_LIMIT] No pre-approval hold. encounterId={} pspId={} amount={}",
                    encounter.getId(),
                    item.getId(),
                    current
            );
            return;
        }

        holdForApproval(context, encounter, insurance, item, current, reason);
    }

    /**
     * Pricing preview: true when billing this line would hold it for pre-approval,
     * so nothing is collected from the patient before the payer decision.
     * Uses the same rule as {@link #applyIfExceeded(BillingProcessingContext)}.
     */
    public boolean requiresApproval(
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal netAmount
    ) {
        if (insurance == null || item == null) {
            return false;
        }
        return holdReason(resolveQuietly(insurance, item), money(netAmount)) != null;
    }

    /**
     * Why the line must wait for pre-approval, or {@code null} when it can be billed.
     * Every contract holds a line that reaches its pre-approval limit; NPHIES contracts
     * also hold on a contract pre-approval that has no limit.
     */
    private String holdReason(CoverageContractResolveDtos.Response resolved, BigDecimal netAmount) {
        if (resolved == null || !resolved.matched() || resolved.preApproval() == null || isExcluded(resolved)) {
            return null;
        }

        CoverageContractResolveDtos.PreApprovalSnapshot preApproval = resolved.preApproval();
        if (preApproval.preApprovalId() == null) {
            return null;
        }

        if (preApproval.limitValue() == null) {
            return CoverageApprovalRoutingService.usesLocalApproval(resolved) ? REASON_CONTRACT : null;
        }

        if (netAmount.signum() <= 0) {
            return null;
        }

        return PreApprovalLimitEvaluator.exceeds(
                preApproval.valueType(),
                preApproval.limitValue(),
                netAmount,
                percentageBase(resolved.limit())
        ) ? REASON_LIMIT : null;
    }

    /**
     * NPHIES contracts: opens a local request for every line already marked
     * PENDING_APPROVAL that has no request yet. Used instead of the Waseel
     * submission, so no eligibility request is needed.
     */
    public int openLocalRequestsForEncounter(Long encounterId) {
        if (encounterId == null) {
            return 0;
        }
        PatientEncounter encounter = patientEncounterRepository.findById(encounterId).orElse(null);
        if (encounter == null || encounter.getPatient() == null) {
            return 0;
        }
        List<PatientServiceAndProduct> items = patientServiceAndProductRepository
                .findByEncounterIdAndPreAuthorizationStatusAndPreAuthorizationRequestIdIsNull(
                        encounterId,
                        PreAuthorizationStatus.PENDING_APPROVAL
                )
                .stream()
                .filter(item -> !Boolean.TRUE.equals(item.getIsBilled()))
                .filter(item -> item.getPaymentStatus() != PaymentStatus.CANCELLED)
                .filter(item -> !item.isUncoveredCashItem())
                .toList();

        int opened = 0;
        for (PatientServiceAndProduct item : items) {
            Long insuranceId = item.getPatientInsuranceId() != null
                    ? item.getPatientInsuranceId()
                    : encounterInsuranceEligibilityService.resolveEncounterPatientInsuranceId(encounterId);
            PatientInsurance insurance = insuranceId == null
                    ? null
                    : patientInsuranceRepository.findById(insuranceId).orElse(null);
            if (insurance == null) {
                LOG.warn(
                        "[PREAUTH_LIMIT] No insurance for pending item, request not opened. encounterId={} pspId={}",
                        encounterId,
                        item.getId()
                );
                continue;
            }
            holdForApproval(null, encounter, insurance, item, itemNet(item), REASON_CONTRACT);
            opened++;
        }
        return opened;
    }

    private boolean canEvaluate(PatientServiceAndProduct item) {
        if (item.getId() == null || item.getEncounterId() == null || item.isUncoveredCashItem()) {
            return false;
        }
        if (Boolean.TRUE.equals(item.getIsExempted()) || item.getPaymentStatus() == PaymentStatus.CANCELLED) {
            return false;
        }
        if (item.getPreAuthorizationRequestId() != null) {
            return false;
        }
        PreAuthorizationStatus status = item.getPreAuthorizationStatus();
        return status == null
                || status == PreAuthorizationStatus.NOT_REQUIRED
                || status == PreAuthorizationStatus.PENDING_APPROVAL;
    }

    private void holdForApproval(
            BillingProcessingContext context,
            PatientEncounter encounter,
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal net,
            String reason
    ) {
        PreAuthorizationRequest request = preAuthorizationRequestRepository.save(PreAuthorizationRequest.builder()
                .patientId(encounter.getPatient().getId())
                .encounterId(encounter.getId())
                .patientInsuranceId(insurance.getId())
                .payorId(insurance.getPayorId())
                .providerId(String.valueOf(encounter.getFacilityId()))
                .dateOrdered(LocalDate.now())
                .payeeId(encounter.getFacilityId())
                .payeeType("provider")
                .preauthType("professional")
                .preauthSubType("op")
                .transfer(false)
                .isNewBorn(false)
                .isCancelled(false)
                .totalNet(net.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .status(REQUEST_STATUS)
                .statusReason(reason)
                .message("This request is waiting for pre-approval. It is not posted to the patient account.")
                .build());

        preAuthorizationItemRepository.save(lineItem(context, request.getId(), item, net));

        item.setPreAuthorizationStatus(PreAuthorizationStatus.PENDING_APPROVAL);
        item.setPreAuthorizationRequired(true);
        item.setPreAuthorizationRequestId(request.getId());
        item.setPaymentStatus(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
        patientServiceAndProductRepository.save(item);

        LOG.info(
                "[PREAUTH_LIMIT] Opened local pre-approval request. encounterId={} pspId={} requestId={} net={}",
                encounter.getId(),
                item.getId(),
                request.getId(),
                net
        );
    }

    private PreAuthorizationItem lineItem(
            BillingProcessingContext context,
            Long requestId,
            PatientServiceAndProduct item,
            BigDecimal net
    ) {
        PriceCalculationResult pricing = context == null ? null : context.getPricingResult();
        BillingPricingInput input = context == null ? null : context.getPricingInput();
        BigDecimal quantity = pricing != null && pricing.quantity() != null
                ? pricing.quantity()
                : (item.getQuantity() == null ? BigDecimal.ONE : BigDecimal.valueOf(item.getQuantity()));
        BigDecimal unitPrice = pricing != null && pricing.unitPrice() != null
                ? pricing.unitPrice()
                : money(item.getUnitPrice());
        BigDecimal discount = pricing != null && pricing.discountAmount() != null
                ? pricing.discountAmount()
                : money(item.getDiscountAmount());
        String code = input != null && input.itemCode() != null && !input.itemCode().isBlank()
                ? input.itemCode()
                : (item.getBillingItemType() == null ? "ITEM-" : item.getBillingItemType().name() + "-") + item.getId();
        String name = input != null ? input.itemName() : null;

        return PreAuthorizationItem.builder()
                .preAuthorizationId(requestId)
                .sequence(1)
                .itemType(item.getBillingItemType() == null ? "SERVICE" : item.getBillingItemType().name())
                .itemCode(trim(code, 100))
                .itemDescription(trim(name, 500))
                .isPackage(false)
                .isMaternity(false)
                .quantity(quantity.setScale(4, RoundingMode.HALF_UP))
                .unitPrice(unitPrice.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .discount(discount.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .factor(BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP))
                .tax(money(pricing == null ? item.getTaxAmount() : pricing.taxAmount()).setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .patientShare(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .payerShare(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .net(net.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .serviceId(item.getServiceId())
                .procedureId(item.getProcedureId())
                .diagnosticTestId(item.getDiagnosticTestId())
                .brandMedicationId(item.getBrandMedicationId())
                .itemDecision("pended")
                .build();
    }

    private BigDecimal itemNet(PatientServiceAndProduct item) {
        BigDecimal net = money(item.getNetAmount());
        if (net.signum() > 0) {
            return net;
        }
        BigDecimal quantity = item.getQuantity() == null ? BigDecimal.ONE : BigDecimal.valueOf(item.getQuantity());
        return money(money(item.getUnitPrice()).multiply(quantity)
                .subtract(money(item.getDiscountAmount()))
                .add(money(item.getTaxAmount())));
    }

    private CoverageContractResolveDtos.Response resolveQuietly(
            PatientInsurance insurance,
            PatientServiceAndProduct item
    ) {
        try {
            return coverageContractShareService.resolveVisitContract(insurance, item);
        } catch (RuntimeException exception) {
            LOG.warn(
                    "[PREAUTH_LIMIT] Coverage contract could not be read for pspId={}",
                    item == null ? null : item.getId(),
                    exception
            );
            return null;
        }
    }

    private boolean isExcluded(CoverageContractResolveDtos.Response resolved) {
        return resolved.exclusion() != null && "YES".equalsIgnoreCase(resolved.exclusion().excludedResult());
    }

    private BigDecimal percentageBase(CoverageContractResolveDtos.CoverageReadingSnapshot limit) {
        if (limit == null || limit.limitValue() == null || !"FIXED".equalsIgnoreCase(limit.valueType())) {
            return null;
        }
        return limit.limitValue();
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
