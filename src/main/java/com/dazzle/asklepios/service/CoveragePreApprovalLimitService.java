package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.CoverageContractResolveDtos;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationItem;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
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

/**
 * Opens a local pre-approval request when a visit line crosses the coverage
 * pre-approval limit. No payer API is called; the charge stays off the patient
 * account until an approval decision exists.
 */
@Service
public class CoveragePreApprovalLimitService {

    static final String REQUEST_STATUS = "Pending Approval";

    private static final Logger LOG = LoggerFactory.getLogger(CoveragePreApprovalLimitService.class);
    private static final int MONEY_SCALE = 2;

    private final CoverageContractShareService coverageContractShareService;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final PreAuthorizationItemRepository preAuthorizationItemRepository;

    public CoveragePreApprovalLimitService(
            CoverageContractShareService coverageContractShareService,
            PatientEncounterRepository patientEncounterRepository,
            PatientInsuranceRepository patientInsuranceRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            PreAuthorizationRequestRepository preAuthorizationRequestRepository,
            PreAuthorizationItemRepository preAuthorizationItemRepository
    ) {
        this.coverageContractShareService = coverageContractShareService;
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.preAuthorizationRequestRepository = preAuthorizationRequestRepository;
        this.preAuthorizationItemRepository = preAuthorizationItemRepository;
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
        if (current.signum() <= 0) {
            return;
        }

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
        if (resolved == null || !resolved.matched() || resolved.preApproval() == null || isExcluded(resolved)) {
            return;
        }
        CoverageContractResolveDtos.PreApprovalSnapshot preApproval = resolved.preApproval();
        if (preApproval.limitValue() == null || preApproval.preApprovalId() == null) {
            return;
        }

        BigDecimal consumed = consumedPostedAmount(encounter.getId(), item.getId(), insurance, preApproval.preApprovalId());
        if (!PreApprovalLimitEvaluator.exceeds(
                preApproval.valueType(),
                preApproval.limitValue(),
                consumed,
                current,
                percentageBase(resolved.limit())
        )) {
            LOG.info(
                    "[PREAUTH_LIMIT] Visit is still within the pre-approval limit. encounterId={} pspId={} consumed={} current={} limit={}",
                    encounter.getId(),
                    item.getId(),
                    consumed,
                    current,
                    preApproval.limitValue()
            );
            return;
        }

        holdForApproval(context, encounter, insurance, item, current);
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
        return status == null || status == PreAuthorizationStatus.NOT_REQUIRED;
    }

    private void holdForApproval(
            BillingProcessingContext context,
            PatientEncounter encounter,
            PatientInsurance insurance,
            PatientServiceAndProduct item,
            BigDecimal net
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
                .statusReason("Coverage pre-approval limit exceeded")
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
        PriceCalculationResult pricing = context.getPricingResult();
        BillingPricingInput input = context.getPricingInput();
        BigDecimal quantity = pricing != null && pricing.quantity() != null
                ? pricing.quantity()
                : BigDecimal.ONE;
        BigDecimal unitPrice = pricing != null && pricing.unitPrice() != null
                ? pricing.unitPrice()
                : BigDecimal.ZERO;
        BigDecimal discount = pricing != null && pricing.discountAmount() != null
                ? pricing.discountAmount()
                : BigDecimal.ZERO;
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
                .tax(money(pricing == null ? null : pricing.taxAmount()).setScale(MONEY_SCALE, RoundingMode.HALF_UP))
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

    private BigDecimal consumedPostedAmount(
            Long encounterId,
            Long currentItemId,
            PatientInsurance insurance,
            Long preApprovalId
    ) {
        BigDecimal consumed = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        for (PatientServiceAndProduct previous : patientServiceAndProductRepository.findByEncounterId(encounterId)) {
            if (!countsAsPosted(previous, currentItemId)) {
                continue;
            }
            if (!matchesPreApproval(insurance, previous, preApprovalId)) {
                continue;
            }
            consumed = consumed.add(money(previous.getNetAmount()));
        }
        return consumed;
    }

    private boolean countsAsPosted(PatientServiceAndProduct previous, Long currentItemId) {
        if (previous == null || previous.getId() == null || previous.getId().equals(currentItemId)) {
            return false;
        }
        if (previous.getPaymentStatus() == PaymentStatus.CANCELLED
                || previous.getPaymentStatus() == PaymentStatus.SKIPPED_PENDING_PRE_AUTH
                || previous.isUncoveredCashItem()) {
            return false;
        }
        PreAuthorizationStatus status = previous.getPreAuthorizationStatus();
        if (status == PreAuthorizationStatus.PENDING_APPROVAL || status == PreAuthorizationStatus.PARTIAL) {
            return false;
        }
        return previous.getNetAmount() != null && previous.getNetAmount().signum() > 0;
    }

    private boolean matchesPreApproval(
            PatientInsurance insurance,
            PatientServiceAndProduct previous,
            Long preApprovalId
    ) {
        CoverageContractResolveDtos.Response resolved = resolveQuietly(insurance, previous);
        return resolved != null
                && resolved.matched()
                && !isExcluded(resolved)
                && resolved.preApproval() != null
                && preApprovalId.equals(resolved.preApproval().preApprovalId());
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
