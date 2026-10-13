package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.EncounterExecutedServiceLineVM;
import com.dazzle.asklepios.web.rest.vm.EncounterExecutedServicesVM;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class EncounterExecutedServicesService {

    private static final String ENTITY_NAME = "encounterExecutedServices";

    private static final EnumSet<BillingChargeStatus> EXCLUDED_CHARGE_STATUSES =
            EnumSet.of(
                    BillingChargeStatus.CANCELLED,
                    BillingChargeStatus.REVERSED
            );

    private static final EnumSet<BillingChargeLineStatus> EXCLUDED_LINE_STATUSES =
            EnumSet.of(BillingChargeLineStatus.REVERSED);

    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final BillingChargeRepository billingChargeRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;
    private final BillingItemDisplayNameService billingItemDisplayNameService;

    public EncounterExecutedServicesService(
            PatientEncounterRepository patientEncounterRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            BillingChargeRepository billingChargeRepository,
            BillingChargeLineRepository billingChargeLineRepository,
            BillingItemDisplayNameService billingItemDisplayNameService
    ) {
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.billingChargeRepository = billingChargeRepository;
        this.billingChargeLineRepository = billingChargeLineRepository;
        this.billingItemDisplayNameService = billingItemDisplayNameService;
    }

    public EncounterExecutedServicesVM getByEncounterId(Long encounterId) {
        PatientEncounter encounter = patientEncounterRepository
                .findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient encounter not found with id " + encounterId,
                        ENTITY_NAME,
                        "encounter.notfound"
                ));

        List<PatientServiceAndProduct> items =
                patientServiceAndProductRepository.findByEncounterId(encounterId);

        List<BillingChargeLine> chargeLines = chargeLines(encounterId);
        Map<String, String> displayNames = new HashMap<>();
        Set<Long> billedItemIds = new HashSet<>();
        List<EncounterExecutedServiceLineVM> lines = new ArrayList<>();

        for (BillingChargeLine chargeLine : chargeLines) {
            PatientServiceAndProduct item = chargeLine.getPatientServiceProduct();
            if (item != null && item.getId() != null) {
                billedItemIds.add(item.getId());
            }
            lines.add(fromChargeLine(chargeLine, item, displayNames));
        }

        for (PatientServiceAndProduct item : items) {
            if (item.getId() != null && billedItemIds.contains(item.getId())) {
                continue;
            }
            lines.add(fromItem(item, displayNames));
        }

        String coverageType = encounter.getCoverageType() == null
                ? null
                : encounter.getCoverageType().name();

        return new EncounterExecutedServicesVM(
                encounter.getId(),
                encounter.getEncounterNumber(),
                patientName(encounter.getPatient()),
                coverageType,
                sum(lines, false),
                sum(lines, true),
                insuranceTotal(lines, coverageType),
                List.copyOf(lines)
        );
    }

    private List<BillingChargeLine> chargeLines(Long encounterId) {
        BillingCharge charge = billingChargeRepository
                .findFirstByEncounter_IdAndStatusNotInOrderByIdDesc(
                        encounterId,
                        EXCLUDED_CHARGE_STATUSES
                )
                .or(() -> billingChargeRepository
                        .findFirstByEncounter_IdOrderByIdDesc(encounterId))
                .orElse(null);

        if (charge == null) {
            return List.of();
        }

        return billingChargeLineRepository
                .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                        encounterId,
                        EXCLUDED_LINE_STATUSES
                )
                .stream()
                .filter(line ->
                        line.getCharge() != null
                                && charge.getId().equals(line.getCharge().getId())
                )
                .toList();
    }

    private EncounterExecutedServiceLineVM fromChargeLine(
            BillingChargeLine line,
            PatientServiceAndProduct item,
            Map<String, String> displayNames
    ) {
        BigDecimal net = ExecutedServiceAmounts.net(
                line.getNetAmount(),
                line.getUnitPrice(),
                line.getQuantity()
        );
        boolean cancelled = ExecutedServiceAmounts.isCancelled(line.getStatus())
                || (item != null && ExecutedServiceAmounts.isCancelled(item.getPaymentStatus()));

        return new EncounterExecutedServiceLineVM(
                line.getId(),
                cancelled,
                paymentLabel(
                        line.getStatus(),
                        item == null ? null : item.getPaymentStatus(),
                        line.getPatientResponsibilityAmount(),
                        line.getAllocatedAmount(),
                        line.getReservedAmount(),
                        line.getOutstandingAmount()
                ),
                line.getCreatedDate(),
                line.getServiceSource() == null ? null : line.getServiceSource().name(),
                line.getBillingItemType() == null ? null : line.getBillingItemType().name(),
                line.getItemCode(),
                billingItemDisplayNameService.resolveDisplayName(
                        line.getBillingItemType(),
                        item,
                        line.getSourceId(),
                        line.getItemDescription(),
                        displayNames
                ),
                line.getQuantity(),
                ExecutedServiceAmounts.priceSource(item == null ? null : item.getPriceSource()),
                ExecutedServiceAmounts.zeroIfNull(line.getUnitPrice()),
                net,
                ExecutedServiceAmounts.patientShare(
                        line.getPatientResponsibilityAmount(),
                        line.getInsuranceResponsibilityAmount(),
                        net
                ),
                ExecutedServiceAmounts.insuranceShare(
                        line.getInsuranceResponsibilityAmount()
                ),
                line.getCurrency() == null ? null : line.getCurrency().name(),
                gross(line.getGrossAmount(), line.getUnitPrice(), line.getQuantity()),
                ExecutedServiceAmounts.zeroIfNull(
                        item == null ? null : item.getPaidAmount()
                )
        );
    }

    private EncounterExecutedServiceLineVM fromItem(
            PatientServiceAndProduct item,
            Map<String, String> displayNames
    ) {
        BigDecimal quantity = item.getQuantity() == null
                ? BigDecimal.ONE
                : BigDecimal.valueOf(item.getQuantity());
        BigDecimal net = ExecutedServiceAmounts.net(
                item.getNetAmount(),
                item.getUnitPrice(),
                quantity
        );

        return new EncounterExecutedServiceLineVM(
                item.getId(),
                ExecutedServiceAmounts.isCancelled(item.getPaymentStatus()),
                paymentLabel(
                        null,
                        item.getPaymentStatus(),
                        item.getPatientShareAmount(),
                        item.getPaidAmount(),
                        BigDecimal.ZERO,
                        item.getRemainingAmount()
                ),
                item.getCreatedDate(),
                item.getServiceSource() == null ? null : item.getServiceSource().name(),
                item.getBillingItemType() == null ? null : item.getBillingItemType().name(),
                null,
                billingItemDisplayNameService.resolveDisplayName(
                        item.getBillingItemType(),
                        item,
                        item.getSourceId(),
                        null,
                        displayNames
                ),
                quantity,
                ExecutedServiceAmounts.priceSource(item.getPriceSource()),
                ExecutedServiceAmounts.zeroIfNull(item.getUnitPrice()),
                net,
                ExecutedServiceAmounts.patientShare(
                        item.getPatientShareAmount(),
                        item.getInsuranceShareAmount(),
                        net
                ),
                ExecutedServiceAmounts.insuranceShare(item.getInsuranceShareAmount()),
                item.getCurrency() == null ? null : item.getCurrency().name(),
                gross(item.getGrossAmount(), item.getUnitPrice(), quantity),
                ExecutedServiceAmounts.zeroIfNull(item.getPaidAmount())
        );
    }

    private BigDecimal gross(
            BigDecimal grossAmount,
            BigDecimal unitPrice,
            BigDecimal quantity
    ) {
        if (grossAmount != null) {
            return grossAmount;
        }

        BigDecimal unit = ExecutedServiceAmounts.zeroIfNull(unitPrice);
        BigDecimal qty = quantity == null || quantity.signum() <= 0
                ? BigDecimal.ONE
                : quantity;
        return unit.multiply(qty);
    }

    private BigDecimal sum(
            List<EncounterExecutedServiceLineVM> lines,
            boolean patient
    ) {
        return lines.stream()
                .filter(line -> !line.cancelled())
                .map(line -> patient ? line.patientShareAmount() : line.netAmount())
                .map(ExecutedServiceAmounts::zeroIfNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal insuranceTotal(
            List<EncounterExecutedServiceLineVM> lines,
            String coverageType
    ) {
        if (coverageType == null
                || !BillingCoverageType.INSURANCE.name()
                .equalsIgnoreCase(coverageType.trim())) {
            return null;
        }

        return lines.stream()
                .filter(line -> !line.cancelled())
                .map(EncounterExecutedServiceLineVM::insuranceShareAmount)
                .map(ExecutedServiceAmounts::zeroIfNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String paymentLabel(
            BillingChargeLineStatus lineStatus,
            PaymentStatus itemStatus,
            BigDecimal patientAmount,
            BigDecimal allocatedAmount,
            BigDecimal reservedAmount,
            BigDecimal outstandingAmount
    ) {
        if (ExecutedServiceAmounts.isCancelled(lineStatus)
                || ExecutedServiceAmounts.isCancelled(itemStatus)) {
            return "CANCELLED";
        }

        if (lineStatus == null) {
            if (itemStatus == PaymentStatus.PAID) {
                return "SETTLED";
            }
            if (itemStatus == PaymentStatus.RESERVED) {
                return "RESERVED";
            }
            if (itemStatus == PaymentStatus.PARTIALLY_PAID
                    || itemStatus == PaymentStatus.PARTIALLY_RESERVED
                    || itemStatus == PaymentStatus.PARTIALLY_DEBIT) {
                return "PARTIAL";
            }
        }

        BigDecimal patient = ExecutedServiceAmounts.zeroIfNull(patientAmount);
        BigDecimal allocated = ExecutedServiceAmounts.zeroIfNull(allocatedAmount);
        BigDecimal reserved = ExecutedServiceAmounts.zeroIfNull(reservedAmount);

        if (outstandingAmount != null
                && outstandingAmount.signum() <= 0
                && allocated.signum() > 0) {
            return "SETTLED";
        }

        BigDecimal remaining = patient
                .subtract(allocated)
                .subtract(reserved)
                .max(BigDecimal.ZERO);
        BigDecimal amountDue = patient.max(remaining.add(allocated).add(reserved));

        if (amountDue.signum() <= 0 || (remaining.signum() <= 0 && allocated.compareTo(amountDue) >= 0)) {
            return "SETTLED";
        }
        if (remaining.signum() <= 0 && allocated.signum() > 0) {
            return "PARTIAL";
        }
        if (reserved.signum() > 0 && remaining.signum() > 0) {
            return "PARTIAL";
        }
        if (reserved.signum() > 0) {
            return "RESERVED";
        }
        return "UNPAID";
    }

    private String patientName(Patient patient) {
        if (patient == null) {
            return "-";
        }

        String name = String.join(
                " ",
                text(patient.getFirstName()),
                text(patient.getSecondName()),
                text(patient.getThirdName()),
                text(patient.getLastName())
        ).trim();

        return name.isBlank() ? "-" : name;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
