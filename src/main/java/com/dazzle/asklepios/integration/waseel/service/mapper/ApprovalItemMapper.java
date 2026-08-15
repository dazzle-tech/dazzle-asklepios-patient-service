package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.integration.waseel.client.WaseelItemMappingClient;
import com.dazzle.asklepios.integration.waseel.client.dto.WaseelItemMappingSetupDTO;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class ApprovalItemMapper {

    private static final String WASEEL_MEDICATION_CODES = "medication-codes";
    private static final String OUTPATIENT_QUANTITY_CODE = "package";

    private final WaseelItemMappingClient waseelItemMappingClient;
    private final PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository;
    private final PatientDiagnosisRepository patientDiagnosisRepository;
    private final MedicationDaysSupplyResolver medicationDaysSupplyResolver;

    public List<WaseelApprovalItem> toWaseelItems(
            List<PatientServiceAndProduct> items,
            BigDecimal patientSharePercent,
            PatientEncounter encounter,
            List<WaseelApprovalSupportingInfo> supportingInfo
    ) {
        return toWaseelItems(items, patientSharePercent, encounter, supportingInfo, false);
    }

    /**
     * @param includeBilled when true, includes already-billed items (used for claim generation)
     */
    public List<WaseelApprovalItem> toWaseelItems(
            List<PatientServiceAndProduct> items,
            BigDecimal patientSharePercent,
            PatientEncounter encounter,
            List<WaseelApprovalSupportingInfo> supportingInfo,
            boolean includeBilled
    ) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        AtomicInteger sequence = new AtomicInteger(1);

        LocalDate itemDate = encounter != null && encounter.getEncounterDate() != null
                ? encounter.getEncounterDate()
                : LocalDate.now();

        List<Integer> supportingInfoSequences = supportingInfo == null
                ? List.of()
                : supportingInfo.stream()
                .map(WaseelApprovalSupportingInfo::sequence)
                .filter(Objects::nonNull)
                .toList();

        Integer nextSupportingInfoSequence = supportingInfoSequences.stream()
                .max(Integer::compareTo)
                .orElse(0) + 1;

        AtomicInteger supportingInfoSequence = new AtomicInteger(nextSupportingInfoSequence);

        return items.stream()
                .filter(item -> includeBilled || Boolean.FALSE.equals(item.getIsBilled()))
                .map(item -> toWaseelItem(
                        item,
                        sequence.getAndIncrement(),
                        patientSharePercent,
                        itemDate,
                        supportingInfoSequences,
                        supportingInfo,
                        supportingInfoSequence
                ))
                .toList();
    }

    public WaseelApprovalItem withInvoiceAmounts(
            WaseelApprovalItem item,
            String invoiceNo,
            BigDecimal unitPrice,
            BigDecimal grossAmount,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal net,
            BigDecimal patientShare,
            BigDecimal payerShare,
            Integer quantity
    ) {
        if (item == null) {
            return null;
        }

        BigDecimal safeNet = money(net);
        BigDecimal safePatientShare = money(patientShare);
        BigDecimal safePayerShare = money(payerShare);
        BigDecimal safeUnitPrice = money(unitPrice);
        BigDecimal safeDiscount = money(discount);
        BigDecimal safeTax = money(tax);

        Integer qty = quantity == null || quantity <= 0 ? item.quantity() : quantity;
        BigDecimal quantityValue = BigDecimal.valueOf(qty == null ? 1 : qty);

        // Insurance invoice lines store unitPrice as the payer-share rate while gross/discount
        // remain proportional to the full charge line — use grossAmount when present.
        BigDecimal gross = grossAmount != null && grossAmount.signum() > 0
                ? money(grossAmount)
                : quantityValue.multiply(safeUnitPrice);

        BigDecimal factor = calculateFactor(gross, safeDiscount);
        BigDecimal formulaNet = gross.multiply(factor).add(safeTax).setScale(2, RoundingMode.HALF_UP);

        if (formulaNet.compareTo(safeNet) != 0 && gross.signum() > 0) {
            factor = safeNet
                    .subtract(safeTax)
                    .divide(gross, 6, RoundingMode.HALF_UP)
                    .max(BigDecimal.ZERO);
        }

        if (safePatientShare.signum() == 0 && safePayerShare.signum() == 0 && safeNet.signum() > 0) {
            safePayerShare = safeNet;
        } else if (safePatientShare.add(safePayerShare).compareTo(safeNet) != 0 && safeNet.signum() > 0) {
            safePayerShare = safeNet.subtract(safePatientShare).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal waseelUnitPrice = gross.signum() > 0 && qty != null && qty > 0
                ? gross.divide(quantityValue, 2, RoundingMode.HALF_UP)
                : safeUnitPrice;

        return new WaseelApprovalItem(
                item.sequence(),
                WaseelItemTypeNormalizer.normalize(item.type()),
                item.itemCode(),
                item.itemDescription(),
                item.nonStandardCode(),
                item.nonStandardDesc(),
                item.isPackage(),
                item.isMaternity(),
                item.bodySite(),
                item.subSite(),
                qty,
                item.quantityCode(),
                waseelUnitPrice,
                safeDiscount,
                factor,
                item.taxPercent(),
                item.patientSharePercent(),
                safeNet,
                safeTax,
                safePatientShare,
                safePayerShare,
                item.startDate(),
                item.endDate(),
                item.supportingInfoSequence(),
                item.careTeamSequence(),
                item.diagnosisSequence(),
                invoiceNo,
                item.itemDetails() == null ? List.of() : item.itemDetails()
        );
    }

    private WaseelApprovalItem toWaseelItem(
            PatientServiceAndProduct item,
            Integer sequence,
            BigDecimal patientSharePercent,
            LocalDate itemDate,
            List<Integer> supportingInfoSequences,
            List<WaseelApprovalSupportingInfo> supportingInfo,
            AtomicInteger supportingInfoSequence
    ) {
        BillingItemTypes billingType = item.getBillingItemType();

        String mappingItemType = getMappingItemType(billingType);
        Long sourceId = getSourceId(item, billingType);

        WaseelItemMappingSetupDTO mapping = getWaseelMapping(mappingItemType, sourceId);

        String waseelItemType = safe(mapping.waseelItemType());

        List<Integer> itemSupportingInfoSequences = new ArrayList<>(supportingInfoSequences);

        if (isMedicationCode(waseelItemType)) {
            Integer daysSupplySequence = addDaysSupply(
                    supportingInfo,
                    supportingInfoSequence,
                    resolveMedicationDaysSupply(item)
            );

            if (daysSupplySequence != null) {
                itemSupportingInfoSequences.add(daysSupplySequence);
            }
        }

        return buildItem(
                item,
                sequence,
                waseelItemType,
                safe(mapping.sbsCode()),
                safe(mapping.sbsDescription()),
                patientSharePercent,
                itemDate,
                itemSupportingInfoSequences,
                resolveDiagnosisSequences(item)
        );
    }

    private WaseelApprovalItem buildItem(
            PatientServiceAndProduct item,
            Integer sequence,
            String type,
            String itemCode,
            String itemDescription,
            BigDecimal patientSharePercent,
            LocalDate itemDate,
            List<Integer> supportingInfoSequences,
            List<Integer> diagnosisSequences
    ) {
        Integer quantity = item.getQuantity() == null
                ? 1
                : item.getQuantity().intValue();

        BigDecimal quantityValue = BigDecimal.valueOf(quantity);
        BigDecimal unitPrice = resolveUnitPrice(item, quantityValue);
        BigDecimal discount = money(item.getDiscountAmount());
        BigDecimal tax = money(item.getTaxAmount());

        BigDecimal gross = quantityValue.multiply(unitPrice);
        BigDecimal factor = calculateFactor(gross, discount);

        BigDecimal net = gross
                .multiply(factor)
                .add(tax)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal patientSharePercentValue = patientSharePercent == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : patientSharePercent.setScale(2, RoundingMode.HALF_UP);

        BigDecimal patientShare = net
                .multiply(patientSharePercentValue)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal payerShare = net
                .subtract(patientShare)
                .setScale(2, RoundingMode.HALF_UP);

        List<Integer> safeSupportingInfoSequences =
                supportingInfoSequences == null || supportingInfoSequences.isEmpty()
                        ? List.of()
                        : supportingInfoSequences;

        return new WaseelApprovalItem(
                sequence,
                WaseelItemTypeNormalizer.normalize(type),
                emptyToNull(itemCode),
                emptyToNull(itemDescription),
                null,
                null,
                false,
                false,
                null,
                null,
                quantity,
                isMedicationCode(type) ? OUTPATIENT_QUANTITY_CODE : null,
                unitPrice,
                discount,
                factor,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                patientSharePercentValue,
                net,
                tax,
                patientShare,
                payerShare,
                itemDate,
                itemDate,
                safeSupportingInfoSequences,
                List.of(1),
                diagnosisSequences == null || diagnosisSequences.isEmpty()
                        ? List.of(1)
                        : diagnosisSequences,
                null,
                List.of()
        );
    }

    private Integer addDaysSupply(
            List<WaseelApprovalSupportingInfo> supportingInfo,
            AtomicInteger sequence,
            Integer daysSupply
    ) {
        if (supportingInfo == null) {
            return null;
        }

        if (daysSupply == null || daysSupply <= 0) {
            throw new RuntimeException("Days of Supply is required for medication-codes item.");
        }

        Integer currentSequence = sequence.getAndIncrement();

        supportingInfo.add(new WaseelApprovalSupportingInfo(
                currentSequence,
                "days-supply",
                null,
                null,
                null,
                String.valueOf(daysSupply),
                null,
                null,
                null,
                null,
                "d",
                null
        ));

        return currentSequence;
    }

    private Integer resolveMedicationDaysSupply(PatientServiceAndProduct item) {
        PatientPrescriptionMedication prescriptionMedication =
                resolvePrescriptionMedication(item);

        if (prescriptionMedication != null) {
            return medicationDaysSupplyResolver.resolve(prescriptionMedication);
        }

        return medicationDaysSupplyResolver.resolve(null);
    }

    private PatientPrescriptionMedication resolvePrescriptionMedication(
            PatientServiceAndProduct item
    ) {
        if (item == null || item.getSourceId() == null) {
            return null;
        }

        if (item.getServiceSource() == ServiceSource.PRESCRIPTION) {
            return patientPrescriptionMedicationRepository
                    .findById(item.getSourceId())
                    .orElse(null);
        }

        return null;
    }

    private List<Integer> resolveDiagnosisSequences(
            PatientServiceAndProduct item
    ) {
        if (item == null
                || item.getBillingItemType() != BillingItemTypes.MEDICATION
                || item.getEncounterId() == null) {
            return List.of(1);
        }

        PatientPrescriptionMedication prescriptionMedication =
                resolvePrescriptionMedication(item);

        if (prescriptionMedication == null || prescriptionMedication.getIndicationIcd() == null) {
            return List.of(1);
        }

        Long indicationIcd = prescriptionMedication.getIndicationIcd();
        AtomicInteger sequence = new AtomicInteger(1);

        for (var diagnosis : patientDiagnosisRepository.findByEncounterId(item.getEncounterId())) {
            int currentSequence = sequence.getAndIncrement();
            if (Objects.equals(diagnosis.getDiagnosisId(), indicationIcd)) {
                return List.of(currentSequence);
            }
        }

        return List.of(1);
    }

    private BigDecimal resolveUnitPrice(PatientServiceAndProduct item, BigDecimal quantityValue) {
        BigDecimal unitPrice = money(item.getUnitPrice());
        if (unitPrice.signum() > 0) {
            return unitPrice;
        }

        BigDecimal fallbackTotal = firstPositive(
                item.getNetAmount(),
                item.getTotalAmount(),
                item.getGrossAmount()
        );
        if (fallbackTotal.signum() <= 0 || quantityValue.signum() <= 0) {
            return unitPrice;
        }

        return fallbackTotal.divide(quantityValue, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal firstPositive(BigDecimal... values) {
        if (values == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        for (BigDecimal value : values) {
            BigDecimal money = money(value);
            if (money.signum() > 0) {
                return money;
            }
        }

        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateFactor(BigDecimal gross, BigDecimal discount) {
        if (gross == null || gross.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
        }

        if (discount == null || discount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal discountPercent = discount.divide(gross, 6, RoundingMode.HALF_UP);

        return BigDecimal.ONE
                .subtract(discountPercent)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private boolean isMedicationCode(String waseelItemType) {
        return WASEEL_MEDICATION_CODES.equalsIgnoreCase(waseelItemType);
    }

    private WaseelItemMappingSetupDTO getWaseelMapping(String itemType, Long sourceId) {
        if (itemType == null || itemType.isBlank() || sourceId == null) {
            throw new RuntimeException("Cannot map Waseel item. Item type or source ID is missing.");
        }

        try {
            WaseelItemMappingSetupDTO mapping = waseelItemMappingClient.getMappingByItem(itemType, sourceId);

            if (mapping == null) {
                throw new RuntimeException(
                        "Missing Waseel SBS mapping for item type: " + itemType + ", source ID: " + sourceId
                );
            }

            if (mapping.waseelItemType() == null || mapping.waseelItemType().isBlank()) {
                throw new RuntimeException(
                        "Missing Waseel item type in mapping for item type: " + itemType + ", source ID: " + sourceId
                );
            }

            if (mapping.sbsCode() == null || mapping.sbsCode().isBlank()) {
                throw new RuntimeException(
                        "Missing Waseel SBS code in mapping for item type: " + itemType + ", source ID: " + sourceId
                );
            }

            return mapping;
        } catch (FeignException.NotFound ex) {
            throw new RuntimeException(
                    "Missing Waseel SBS mapping for item type: " + itemType + ", source ID: " + sourceId
            );
        }
    }

    private String getMappingItemType(BillingItemTypes type) {
        if (type == BillingItemTypes.PROCEDURE) return "PROCEDURE";
        if (type == BillingItemTypes.SERVICE) return "SERVICE";
        if (type == BillingItemTypes.LABORATORY || type == BillingItemTypes.PATHOLOGY) return "LABORATORY";
        if (type == BillingItemTypes.RADIOLOGY) return "RADIOLOGY";
        if (type == BillingItemTypes.MEDICATION) return "MEDICATION";

        throw new RuntimeException("Unsupported billing item type for Waseel mapping: " + type);
    }

    private Long getSourceId(PatientServiceAndProduct item, BillingItemTypes type) {
        if (type == BillingItemTypes.PROCEDURE) return item.getProcedureId();
        if (type == BillingItemTypes.SERVICE) return item.getServiceId();

        if (type == BillingItemTypes.LABORATORY
                || type == BillingItemTypes.RADIOLOGY
                || type == BillingItemTypes.PATHOLOGY) {
            return item.getDiagnosticTestId();
        }

        if (type == BillingItemTypes.MEDICATION) return item.getBrandMedicationId();

        return null;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}