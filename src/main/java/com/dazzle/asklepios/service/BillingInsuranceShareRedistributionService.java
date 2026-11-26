package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import com.dazzle.asklepios.domain.enumeration.billing.ReservationReleaseReason;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.service.dto.billing.BillingChargeLineReversalResult;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Re-spreads the visit max-limit pool over every active insurance line of an
 * encounter, paid lines included, after a line leaves the pool.
 *
 * Paid lines give their wallet money back first, are recalculated, and then
 * take back from the wallet up to the new patient share. Any extra patient
 * share stays outstanding; any freed amount stays in the wallet.
 */
@Service
@RequiredArgsConstructor
public class BillingInsuranceShareRedistributionService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingInsuranceShareRedistributionService.class
            );

    private static final int MONEY_SCALE = 4;

    private static final BillingLedgerSourceChannel SOURCE_CHANNEL =
            BillingLedgerSourceChannel.API;

    private final BillingChargeLineRepository billingChargeLineRepository;

    private final BillingChargeRepository billingChargeRepository;

    private final PatientInsuranceRepository patientInsuranceRepository;

    private final PatientEncounterRepository patientEncounterRepository;

    private final InsurancePatientShareCalculator insurancePatientShareCalculator;

    private final BillingResponsibilityService billingResponsibilityService;

    private final BillingAllocationReversalCoordinator billingAllocationReversalCoordinator;

    private final BillingAllocationService billingAllocationService;

    private final BillingReservationService billingReservationService;

    private final BillingWalletService billingWalletService;

    private final BillingChargeService billingChargeService;

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public void redistributeVisitMaxLimit(
            Long encounterId,
            Long patientInsuranceId,
            String reason,
            String performedBy,
            String requestId
    ) {
        if (encounterId == null || patientInsuranceId == null) {
            return;
        }

        PatientInsurance insurance =
                patientInsuranceRepository
                        .findById(patientInsuranceId)
                        .orElse(null);

        if (insurance == null) {
            return;
        }

        VisitMaxLimitTracker visitMaxLimitTracker =
                insurancePatientShareCalculator
                        .createVisitMaxLimitTracker(insurance);

        /*
         * Without a visit max limit every line is split on its own, so
         * removing one line cannot change the others.
         */
        if (!visitMaxLimitTracker.hasLimit()) {
            return;
        }

        List<BillingChargeLine> poolLines =
                billingChargeLineRepository
                        .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                encounterId,
                                List.of(
                                        BillingChargeLineStatus.CANCELLED,
                                        BillingChargeLineStatus.REVERSED
                                )
                        )
                        .stream()
                        .filter(line -> sharesVisitPool(line, patientInsuranceId))
                        .toList();

        if (poolLines.isEmpty()) {
            return;
        }

        LOG.info(
                "[MAX_LIMIT_REDISTRIBUTION] Started encounterId={} "
                        + "patientInsuranceId={} lines={} requestId={}",
                encounterId,
                patientInsuranceId,
                poolLines.size(),
                requestId
        );

        Map<Long, BillingCharge> charges = new LinkedHashMap<>();

        for (BillingChargeLine line : poolLines) {
            BigDecimal walletReturned =
                    reverseWalletAllocations(
                            line,
                            reason,
                            performedBy,
                            requestId
                    );

            BillingProcessingContext context =
                    billingResponsibilityService
                            .recalculateInsuranceResponsibility(
                                    line,
                                    visitMaxLimitTracker,
                                    reason
                            );

            billingReservationService
                    .relinkActiveReservationsToPatientResponsibility(
                            context
                    );

            billingReservationService
                    .releaseExcessAfterRepricing(
                            context,
                            ReservationReleaseReason.AMOUNT_REDUCED,
                            performedBy
                    );

            reallocateFromWallet(
                    context,
                    walletReturned,
                    requestId
            );

            if (line.getCharge() != null) {
                charges.putIfAbsent(
                        line.getCharge().getId(),
                        line.getCharge()
                );
            }
        }

        charges.values().forEach(charge ->
                recalculateCharge(charge, poolLines)
        );

        LOG.info(
                "[MAX_LIMIT_REDISTRIBUTION] Completed encounterId={} "
                        + "patientInsuranceId={} requestId={}",
                encounterId,
                patientInsuranceId,
                requestId
        );
    }

    private boolean sharesVisitPool(
            BillingChargeLine line,
            Long patientInsuranceId
    ) {
        PatientServiceAndProduct item =
                line.getPatientServiceProduct();

        return item != null
                && Objects.equals(item.getPatientInsuranceId(), patientInsuranceId)
                && !Boolean.TRUE.equals(item.getIsExempted())
                && !item.isUncoveredCashItem()
                && item.getPreAuthorizationStatus() != PreAuthorizationStatus.REJECTED;
    }

    /**
     * Returns the money already applied to the line, so the patient share can
     * be recalculated. Only the wallet part can be applied again afterwards;
     * a reversed debit simply lowers the patient's debt.
     */
    private BigDecimal reverseWalletAllocations(
            BillingChargeLine line,
            String reason,
            String performedBy,
            String requestId
    ) {
        if (money(line.getAllocatedAmount()).signum() <= 0) {
            return zero();
        }

        BillingChargeLineReversalResult reversal =
                billingAllocationReversalCoordinator
                        .reverseActiveAllocationsForChargeLine(
                                line.getId(),
                                reason,
                                performedBy,
                                requestId + ":REDISTRIBUTE:" + line.getId(),
                                SOURCE_CHANNEL
                        );

        return money(reversal.walletAllocationReversedAmount());
    }

    private void reallocateFromWallet(
            BillingProcessingContext context,
            BigDecimal walletReturned,
            String requestId
    ) {
        BillingChargeResponsibility patientResponsibility =
                context.getPatientResponsibility();

        if (walletReturned.signum() <= 0 || patientResponsibility == null) {
            return;
        }

        BillingChargeLine line = context.getChargeLine();
        PatientServiceAndProduct item = context.getPatientServiceProduct();

        BillingWallet wallet =
                billingWalletService.findOptionalByPatientAndCurrency(
                        item.getPatientId(),
                        line.getCurrency()
                );

        BigDecimal patientUnpaid =
                money(patientResponsibility.getOutstandingAmount())
                        .subtract(money(line.getReservedAmount()));

        BigDecimal amount =
                walletReturned
                        .min(patientUnpaid)
                        .min(wallet == null ? zero() : money(wallet.getAvailableBalance()));

        if (amount.signum() <= 0) {
            return;
        }

        billingAllocationService.allocateFromAvailableWallet(
                patientResponsibility.getId(),
                amount,
                requestId + ":REALLOCATE:" + line.getId(),
                SOURCE_CHANNEL
        );
    }

    /**
     * A financially closed charge is opened again when the new split leaves
     * the patient something to pay, so it can still be collected before the
     * invoice is generated.
     */
    private void recalculateCharge(
            BillingCharge charge,
            List<BillingChargeLine> poolLines
    ) {
        BillingCharge current =
                billingChargeRepository
                        .findById(charge.getId())
                        .orElse(charge);

        if (current.getStatus() == BillingChargeStatus.CLOSED
                && hasPatientBalance(current, poolLines)) {
            current.setStatus(BillingChargeStatus.OPEN);
            current.setClosedDate(null);
            billingChargeRepository.save(current);

            reopenEncounterBilling(current);
        }

        billingChargeService.recalculateChargeTotals(
                BillingProcessingContext.builder()
                        .charge(current)
                        .build()
        );
    }

    private boolean hasPatientBalance(
            BillingCharge charge,
            List<BillingChargeLine> poolLines
    ) {
        return poolLines.stream()
                .filter(line -> line.getCharge() != null
                        && Objects.equals(line.getCharge().getId(), charge.getId()))
                .anyMatch(line ->
                        money(line.getPatientResponsibilityAmount())
                                .subtract(money(line.getAllocatedAmount()))
                                .subtract(money(line.getReservedAmount()))
                                .signum() > 0
                );
    }

    private void reopenEncounterBilling(BillingCharge charge) {
        if (charge.getEncounter() == null) {
            return;
        }

        patientEncounterRepository
                .findById(charge.getEncounter().getId())
                .filter(encounter ->
                        encounter.getBillingStatus()
                                == EncounterBillingStatus.FINANCIALLY_CLOSED
                )
                .ifPresent(this::reopenEncounterBilling);
    }

    private void reopenEncounterBilling(PatientEncounter encounter) {
        encounter.setBillingStatus(EncounterBillingStatus.OPEN);
        encounter.setFinanciallyClosedAt(null);
        encounter.setFinanciallyClosedBy(null);
        patientEncounterRepository.save(encounter);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? zero()
                : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
