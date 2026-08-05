package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingAllocation;
import com.dazzle.asklepios.domain.enumeration.billing.AllocationSourceType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingAllocationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import com.dazzle.asklepios.repository.BillingAllocationRepository;
import com.dazzle.asklepios.service.dto.billing.BillingAllocationReversalResult;
import com.dazzle.asklepios.service.dto.billing.BillingChargeLineReversalResult;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BillingAllocationReversalCoordinator {

    private static final String ENTITY_NAME =
            "billingAllocationReversal";

    private static final int MONEY_SCALE = 4;

    private final BillingAllocationRepository
            billingAllocationRepository;

    private final BillingAllocationService
            billingAllocationService;

    private final BillingDebitService
            billingDebitService;

    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class
    )
    public BillingChargeLineReversalResult
    reverseActiveAllocationsForChargeLine(
            Long chargeLineId,
            String reason,
            String reversedBy,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        validateInput(
                chargeLineId,
                reason,
                reversedBy,
                requestId,
                sourceChannel
        );

        List<BillingAllocation> allocations =
                billingAllocationRepository
                        .findAllByChargeLine_IdAndStatusInOrderByAllocationDateDescIdDesc(
                                chargeLineId,
                                EnumSet.of(
                                        BillingAllocationStatus.ACTIVE,
                                        BillingAllocationStatus.PARTIALLY_REVERSED
                                )
                        );

        BigDecimal walletReversed = zero();
        BigDecimal debitReversed = zero();

        for (BillingAllocation allocation : allocations) {
            BigDecimal remaining =
                    money(
                            allocation.getRemainingAllocatedAmount()
                    );

            if (remaining.signum() <= 0) {
                continue;
            }

            String allocationRequestId =
                    requestId.trim()
                            + ":ALLOCATION:"
                            + allocation.getId();

            if (allocation.getAllocationSourceType()
                    == AllocationSourceType.DEBIT) {

                BigDecimal reversed =
                        billingDebitService
                                .reverseDebitAllocation(
                                        allocation,
                                        reason,
                                        reversedBy,
                                        allocationRequestId
                                                + ":DEBIT",
                                        sourceChannel
                                );

                debitReversed =
                        debitReversed.add(
                                money(reversed)
                        );

                continue;
            }

            if (allocation.getAllocationSourceType()
                    == AllocationSourceType.RESERVATION
                    || allocation.getAllocationSourceType()
                    == AllocationSourceType.WALLET_AVAILABLE
                    || allocation.getAllocationSourceType()
                    == AllocationSourceType.PAYMENT) {

                BillingAllocationReversalResult result =
                        billingAllocationService
                                .reverseAllocation(
                                        allocation.getId(),
                                        remaining,
                                        reason,
                                        reversedBy,
                                        allocationRequestId
                                                + ":WALLET",
                                        sourceChannel
                                );

                walletReversed =
                        walletReversed.add(
                                money(
                                        result.reversedAmount()
                                )
                        );

                continue;
            }

            throw new BadRequestAlertException(
                    "Unsupported allocation source for automatic reversal: "
                            + allocation.getAllocationSourceType(),
                    ENTITY_NAME,
                    "allocationSource.unsupported"
            );
        }

        return new BillingChargeLineReversalResult(
                walletReversed,
                debitReversed,
                walletReversed.add(debitReversed)
        );
    }

    private void validateInput(
            Long chargeLineId,
            String reason,
            String reversedBy,
            String requestId,
            BillingLedgerSourceChannel sourceChannel
    ) {
        if (chargeLineId == null) {
            throw new BadRequestAlertException(
                    "Charge-line ID is required.",
                    ENTITY_NAME,
                    "chargeLineId.required"
            );
        }

        if (reason == null
                || reason.isBlank()) {
            throw new BadRequestAlertException(
                    "Reversal reason is required.",
                    ENTITY_NAME,
                    "reason.required"
            );
        }

        if (reversedBy == null
                || reversedBy.isBlank()) {
            throw new BadRequestAlertException(
                    "Reversed-by user is required.",
                    ENTITY_NAME,
                    "reversedBy.required"
            );
        }

        if (requestId == null
                || requestId.isBlank()) {
            throw new BadRequestAlertException(
                    "Request ID is required.",
                    ENTITY_NAME,
                    "requestId.required"
            );
        }

        if (sourceChannel == null) {
            throw new BadRequestAlertException(
                    "Ledger source channel is required.",
                    ENTITY_NAME,
                    "sourceChannel.required"
            );
        }
    }

    private BigDecimal money(
            BigDecimal value
    ) {
        return value == null
                ? zero()
                : value.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }
}