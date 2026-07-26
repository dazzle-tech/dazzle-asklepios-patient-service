package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.service.dto.billing.BillingResponsibilitySummary;
import com.dazzle.asklepios.service.dto.billing.BillingWalletSummary;
import com.dazzle.asklepios.service.dto.billing.EncounterBillingItemSummary;
import com.dazzle.asklepios.service.dto.billing.EncounterBillingSummary;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingPatientViewService {

    private static final String ENTITY_NAME =
            "billingPatientView";

    private static final int MONEY_SCALE = 4;

    private static final EnumSet<BillingChargeLineStatus>
            EXCLUDED_LINE_STATUSES =
            EnumSet.of(
                    BillingChargeLineStatus.CANCELLED,
                    BillingChargeLineStatus.REVERSED
            );

    private static final EnumSet<BillingResponsibilityStatus>
            EXCLUDED_RESPONSIBILITY_STATUSES =
            EnumSet.of(
                    BillingResponsibilityStatus.CANCELLED
            );

    private final PatientEncounterRepository
            patientEncounterRepository;

    private final BillingChargeLineRepository
            billingChargeLineRepository;

    private final BillingChargeResponsibilityRepository
            billingChargeResponsibilityRepository;

    private final BillingWalletService
            billingWalletService;

    public EncounterBillingSummary getEncounterSummary(
            Long encounterId
    ) {
        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "Encounter ID is required.",
                    ENTITY_NAME,
                    "encounterId.required"
            );
        }

        PatientEncounter encounter =
                patientEncounterRepository
                        .findById(encounterId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Encounter not found with id "
                                                + encounterId,
                                        ENTITY_NAME,
                                        "encounter.notfound"
                                )
                        );

        if (encounter.getPatient() == null
                || encounter.getPatient().getId() == null) {
            throw new BadRequestAlertException(
                    "Encounter patient is missing.",
                    ENTITY_NAME,
                    "encounter.patient.missing"
            );
        }

        Long patientId =
                encounter.getPatient().getId();

        BillingWallet wallet =
                billingWalletService
                        .findOptionalByPatient(
                                patientId
                        );

        List<BillingChargeLine> lines =
                billingChargeLineRepository
                        .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                encounterId,
                                EXCLUDED_LINE_STATUSES
                        );

        if (lines.isEmpty()) {
            return emptySummary(
                    encounterId,
                    patientId,
                    wallet
            );
        }

        BillingCharge charge =
                requireSingleCharge(
                        encounterId,
                        lines
                );

        List<BillingChargeResponsibility> responsibilities =
                billingChargeResponsibilityRepository
                        .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                                encounterId,
                                EXCLUDED_RESPONSIBILITY_STATUSES
                        );

        Map<Long, List<BillingChargeResponsibility>>
                responsibilitiesByLineId =
                responsibilities.stream()
                        .filter(responsibility ->
                                responsibility.getChargeLine() != null
                                        && responsibility
                                        .getChargeLine()
                                        .getId() != null
                        )
                        .collect(
                                Collectors.groupingBy(
                                        responsibility ->
                                                responsibility
                                                        .getChargeLine()
                                                        .getId()
                                )
                        );

        List<EncounterBillingItemSummary> items =
                lines.stream()
                        .map(line ->
                                mapItem(
                                        line,
                                        responsibilitiesByLineId
                                                .getOrDefault(
                                                        line.getId(),
                                                        List.of()
                                                )
                                )
                        )
                        .toList();

        ResponsibilityTotals patientTotals =
                calculateTotals(
                        responsibilities,
                        ResponsiblePartyType.PATIENT
                );

        ResponsibilityTotals insuranceTotals =
                calculateTotals(
                        responsibilities,
                        ResponsiblePartyType.INSURANCE
                );

        ResponsibilityTotals otherPayerTotals =
                calculateTotals(
                        responsibilities,
                        ResponsiblePartyType.OTHER_PAYER
                );

        return new EncounterBillingSummary(
                charge.getId(),
                charge.getChargeNumber(),
                patientId,
                encounterId,
                charge.getChargeDate(),
                charge.getCurrency(),

                money(charge.getGrossAmount()),
                money(charge.getDiscountAmount()),
                money(charge.getExemptionAmount()),
                money(charge.getTaxAmount()),
                money(charge.getNetAmount()),
                money(charge.getAllocatedAmount()),
                money(charge.getOutstandingAmount()),

                charge.getLineCount(),
                charge.getStatus(),

                patientTotals.total(),
                patientTotals.allocated(),
                patientTotals.outstanding(),

                zero(),
                zero(),

                insuranceTotals.total(),
                insuranceTotals.allocated(),
                insuranceTotals.outstanding(),

                otherPayerTotals.total(),
                otherPayerTotals.allocated(),
                otherPayerTotals.outstanding(),

                mapWallet(wallet),
                items
        );
    }

    private BillingCharge requireSingleCharge(
            Long encounterId,
            List<BillingChargeLine> lines
    ) {
        List<BillingCharge> charges =
                lines.stream()
                        .map(
                                BillingChargeLine::getCharge
                        )
                        .filter(charge ->
                                charge != null
                                        && charge.getId() != null
                        )
                        .collect(
                                Collectors.toMap(
                                        BillingCharge::getId,
                                        charge -> charge,
                                        (first, second) -> first
                                )
                        )
                        .values()
                        .stream()
                        .toList();

        if (charges.isEmpty()) {
            throw new BadRequestAlertException(
                    "Encounter billing lines do not have a charge header.",
                    ENTITY_NAME,
                    "charge.missing"
            );
        }

        if (charges.size() > 1) {
            throw new BadRequestAlertException(
                    "More than one active charge was found for encounter "
                            + encounterId
                            + ". The current billing version expects one organization-currency charge.",
                    ENTITY_NAME,
                    "encounter.multipleActiveCharges"
            );
        }

        return charges.get(0);
    }

    private EncounterBillingItemSummary mapItem(
            BillingChargeLine line,
            List<BillingChargeResponsibility> responsibilities
    ) {
        ResponsibilityTotals patientTotals =
                calculateTotals(
                        responsibilities,
                        ResponsiblePartyType.PATIENT
                );

        ResponsibilityTotals insuranceTotals =
                calculateTotals(
                        responsibilities,
                        ResponsiblePartyType.INSURANCE
                );

        ResponsibilityTotals otherPayerTotals =
                calculateTotals(
                        responsibilities,
                        ResponsiblePartyType.OTHER_PAYER
                );

        return new EncounterBillingItemSummary(
                line.getPatientServiceProduct() == null
                        ? null
                        : line
                        .getPatientServiceProduct()
                        .getId(),

                line.getId(),

                line.getBillingItemType() == null
                        ? null
                        : line.getBillingItemType().name(),

                line.getSourceId(),

                line.getItemCode(),

                line.getItemDescription(),

                toDecimal(
                        line.getQuantity()
                ),

                money(line.getUnitPrice()),

                null,
                null,
                null,

                money(line.getGrossAmount()),

                money(line.getDiscountAmount()),

                money(line.getExemptionAmount()),

                money(line.getTaxAmount()),

                money(line.getNetAmount()),

                patientTotals.total(),

                insuranceTotals.total(),

                otherPayerTotals.total(),

                money(line.getReservedAmount()),

                money(line.getAllocatedAmount()),

                money(line.getOutstandingAmount()),

                line.getPatientServiceProduct() != null
                        && Boolean.TRUE.equals(
                        line.getPatientServiceProduct()
                                .getIsExempted()
                ),

                line.getCurrency(),

                line.getStatus(),

                line.getCreatedDate(),

                responsibilities.stream()
                        .map(this::mapResponsibility)
                        .toList()
        );
    }

    private BillingResponsibilitySummary mapResponsibility(
            BillingChargeResponsibility responsibility
    ) {
        return new BillingResponsibilitySummary(
                responsibility.getId(),

                responsibility.getResponsiblePartyType(),

                responsibility.getResponsibilityRole(),

                responsibility.getPayerId(),

                responsibility.getPatientInsurance() == null
                        ? null
                        : responsibility
                        .getPatientInsurance()
                        .getId(),

                money(
                        responsibility.getResponsibilityAmount()
                ),

                money(
                        responsibility.getAllocatedAmount()
                ),

                money(
                        responsibility.getOutstandingAmount()
                ),

                percentage(
                        responsibility.getCoveragePercentage()
                ),

                money(
                        responsibility.getDeductibleAmount()
                ),

                money(
                        responsibility.getCopayAmount()
                ),

                money(
                        responsibility.getCoinsuranceAmount()
                ),

                money(
                        responsibility.getNonCoveredAmount()
                ),

                responsibility.getCurrency(),

                responsibility.getStatus()
        );
    }

    private ResponsibilityTotals calculateTotals(
            List<BillingChargeResponsibility> responsibilities,
            ResponsiblePartyType partyType
    ) {
        BigDecimal total =
                responsibilities.stream()
                        .filter(responsibility ->
                                responsibility
                                        .getResponsiblePartyType()
                                        == partyType
                        )
                        .map(
                                BillingChargeResponsibility::
                                        getResponsibilityAmount
                        )
                        .map(this::money)
                        .reduce(
                                zero(),
                                BigDecimal::add
                        );

        BigDecimal allocated =
                responsibilities.stream()
                        .filter(responsibility ->
                                responsibility
                                        .getResponsiblePartyType()
                                        == partyType
                        )
                        .map(
                                BillingChargeResponsibility::
                                        getAllocatedAmount
                        )
                        .map(this::money)
                        .reduce(
                                zero(),
                                BigDecimal::add
                        );

        BigDecimal outstanding =
                responsibilities.stream()
                        .filter(responsibility ->
                                responsibility
                                        .getResponsiblePartyType()
                                        == partyType
                        )
                        .map(
                                BillingChargeResponsibility::
                                        getOutstandingAmount
                        )
                        .map(this::money)
                        .reduce(
                                zero(),
                                BigDecimal::add
                        );

        return new ResponsibilityTotals(
                total,
                allocated,
                outstanding
        );
    }

    private BillingWalletSummary mapWallet(
            BillingWallet wallet
    ) {
        if (wallet == null) {
            return new BillingWalletSummary(
                    null,
                    zero(),
                    zero(),
                    zero(),
                    zero(),
                    zero(),
                    null,
                    null
            );
        }

        return new BillingWalletSummary(
                wallet.getId(),
                money(wallet.getCreditedAmount()),
                money(wallet.getAvailableBalance()),
                money(wallet.getReservedBalance()),
                money(wallet.getConsumedAmount()),
                money(wallet.getRefundedAmount()),
                wallet.getCurrency(),
                wallet.getStatus()
        );
    }

    private EncounterBillingSummary emptySummary(
            Long encounterId,
            Long patientId,
            BillingWallet wallet
    ) {
        return new EncounterBillingSummary(
                null,
                null,
                patientId,
                encounterId,
                null,
                wallet == null
                        ? null
                        : wallet.getCurrency(),

                zero(),
                zero(),
                zero(),
                zero(),
                zero(),
                zero(),
                zero(),

                0,
                null,

                zero(),
                zero(),
                zero(),

                zero(),
                zero(),

                zero(),
                zero(),
                zero(),

                zero(),
                zero(),
                zero(),

                mapWallet(wallet),
                List.of()
        );
    }

    private BigDecimal toDecimal(
            Number value
    ) {
        return value == null
                ? zero()
                : new BigDecimal(
                value.toString()
        ).setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
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

    private BigDecimal percentage(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO.setScale(
                6,
                RoundingMode.HALF_UP
        )
                : value.setScale(
                6,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private record ResponsibilityTotals(
            BigDecimal total,
            BigDecimal allocated,
            BigDecimal outstanding
    ) {
    }
}
