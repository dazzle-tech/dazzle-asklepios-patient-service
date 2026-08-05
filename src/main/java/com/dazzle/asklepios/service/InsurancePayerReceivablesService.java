package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.ClaimRequestRepository;
import com.dazzle.asklepios.service.dto.billing.InsurancePayerReceivablesSummaryResponse;
import com.dazzle.asklepios.service.helper.PayorHelper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsurancePayerReceivablesService {

    private static final Logger LOG =
            LoggerFactory.getLogger(InsurancePayerReceivablesService.class);

    private static final int MONEY_SCALE = 4;

    private static final EnumSet<BillingResponsibilityStatus>
            EXCLUDED_RESPONSIBILITY_STATUSES =
            EnumSet.of(
                    BillingResponsibilityStatus.CANCELLED,
                    BillingResponsibilityStatus.REVERSED,
                    BillingResponsibilityStatus.SUPERSEDED
            );

    private static final EnumSet<ClaimStatus> PENDING_CLAIM_STATUSES =
            EnumSet.of(
                    ClaimStatus.DRAFT,
                    ClaimStatus.SUBMITTING,
                    ClaimStatus.SUBMITTED
            );

    private static final EnumSet<ClaimStatus> PAID_CLAIM_STATUSES =
            EnumSet.of(ClaimStatus.ACCEPTED);

    private static final EnumSet<ClaimStatus> REJECTED_CLAIM_STATUSES =
            EnumSet.of(
                    ClaimStatus.REJECTED,
                    ClaimStatus.FAILED
            );

    private final BillingChargeResponsibilityRepository
            billingChargeResponsibilityRepository;

    private final ClaimRequestRepository claimRequestRepository;

    private final PayorHelper payorHelper;

    public List<InsurancePayerReceivablesSummaryResponse> summarizeByPayer(
            Long facilityId
    ) {
        LOG.debug(
                "[INSURANCE_RECEIVABLES] Summarizing payer receivables facilityId={}",
                facilityId
        );

        Map<Long, MutableSummary> summaries = new TreeMap<>();

        billingChargeResponsibilityRepository
                .aggregateInsuranceTotalsByPayer(
                        ResponsiblePartyType.INSURANCE,
                        EXCLUDED_RESPONSIBILITY_STATUSES,
                        facilityId
                )
                .forEach(row -> applyFinancialTotals(summaries, row));

        billingChargeResponsibilityRepository
                .countPartiallyPaidClaimsByPayer(
                        ResponsiblePartyType.INSURANCE,
                        EXCLUDED_RESPONSIBILITY_STATUSES,
                        facilityId
                )
                .forEach(row ->
                        summaries
                                .computeIfAbsent(
                                        (Long) row[0],
                                        payerId -> new MutableSummary()
                                )
                                .partiallyPaidClaims =
                                ((Number) row[1]).longValue()
                );

        claimRequestRepository
                .countClaimsByPayerAndStatus(facilityId)
                .forEach(row -> applyClaimCounts(summaries, row));

        return summaries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void applyFinancialTotals(
            Map<Long, MutableSummary> summaries,
            Object[] row
    ) {
        Long payerId = (Long) row[0];
        Currency currency = (Currency) row[1];

        MutableSummary summary =
                summaries.computeIfAbsent(
                        payerId,
                        id -> new MutableSummary()
                );

        summary.currency = currency;
        summary.totalBilled = money((BigDecimal) row[2]);
        summary.totalReceived = money((BigDecimal) row[3]);
        summary.outstandingBalance = money((BigDecimal) row[4]);
    }

    private void applyClaimCounts(
            Map<Long, MutableSummary> summaries,
            Object[] row
    ) {
        Long payerId = (Long) row[0];
        ClaimStatus status = (ClaimStatus) row[1];
        long count = ((Number) row[2]).longValue();

        MutableSummary summary =
                summaries.computeIfAbsent(
                        payerId,
                        id -> new MutableSummary()
                );

        if (PENDING_CLAIM_STATUSES.contains(status)) {
            summary.pendingClaims += count;
        } else if (PAID_CLAIM_STATUSES.contains(status)) {
            summary.paidClaims += count;
        } else if (REJECTED_CLAIM_STATUSES.contains(status)) {
            summary.rejectedClaims += count;
        }
    }

    private InsurancePayerReceivablesSummaryResponse toResponse(
            Long payerId,
            MutableSummary summary
    ) {
        PayorDTO payor = payorHelper.findPayor(payerId, null);
        String payerName = payor == null ? null : payor.name();

        return new InsurancePayerReceivablesSummaryResponse(
                payerId,
                payerName,
                summary.currency,
                summary.totalBilled,
                summary.totalReceived,
                summary.outstandingBalance,
                summary.pendingClaims,
                summary.paidClaims,
                summary.partiallyPaidClaims,
                summary.rejectedClaims,
                summary.cancelledClaims,
                resolveOverallStatus(summary)
        );
    }

    private String resolveOverallStatus(MutableSummary summary) {
        if (summary.outstandingBalance.signum() <= 0
                && summary.totalBilled.signum() > 0) {
            return "SETTLED";
        }

        if (summary.pendingClaims > 0) {
            return "PENDING_CLAIMS";
        }

        if (summary.outstandingBalance.signum() > 0) {
            return "OUTSTANDING";
        }

        if (summary.totalBilled.signum() == 0) {
            return "NO_ACTIVITY";
        }

        return "IN_PROGRESS";
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static final class MutableSummary {
        private Currency currency;
        private BigDecimal totalBilled = BigDecimal.ZERO;
        private BigDecimal totalReceived = BigDecimal.ZERO;
        private BigDecimal outstandingBalance = BigDecimal.ZERO;
        private long pendingClaims;
        private long paidClaims;
        private long partiallyPaidClaims;
        private long rejectedClaims;
        private long cancelledClaims;
    }
}
