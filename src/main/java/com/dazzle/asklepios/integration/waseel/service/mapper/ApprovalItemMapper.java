package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.integration.waseel.client.WaseelItemMappingClient;
import com.dazzle.asklepios.integration.waseel.client.dto.WaseelItemMappingSetupDTO;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
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

    private final WaseelItemMappingClient waseelItemMappingClient;

    public List<WaseelApprovalItem> toWaseelItems(
            List<PatientServiceAndProduct> items,
            BigDecimal patientSharePercent,
            PatientEncounter encounter,
            List<WaseelApprovalSupportingInfo> supportingInfo
    ) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        AtomicInteger itemSequence = new AtomicInteger(1);

        LocalDate itemDate = encounter != null && encounter.getEncounterDate() != null
                ? encounter.getEncounterDate()
                : LocalDate.now();

        List<Integer> existingSupportingInfoSequences = supportingInfo == null
                ? List.of()
                : supportingInfo.stream()
                .map(WaseelApprovalSupportingInfo::sequence)
                .filter(Objects::nonNull)
                .toList();

        Integer nextSupportingInfoSequence = existingSupportingInfoSequences.stream()
                .max(Integer::compareTo)
                .orElse(0) + 1;

        AtomicInteger supportingInfoSequence = new AtomicInteger(nextSupportingInfoSequence);

        return items.stream()
                .filter(item -> Boolean.FALSE.equals(item.getIsBilled()))
                .map(item -> toWaseelItem(
                        item,
                        itemSequence.getAndIncrement(),
                        patientSharePercent,
                        itemDate,
                        existingSupportingInfoSequences,
                        supportingInfo,
                        supportingInfoSequence
                ))
                .toList();
    }

    private WaseelApprovalItem toWaseelItem(
            PatientServiceAndProduct item,
            Integer sequence,
            BigDecimal patientSharePercent,
            LocalDate itemDate,
            List<Integer> existingSupportingInfoSequences,
            List<WaseelApprovalSupportingInfo> supportingInfo,
            AtomicInteger supportingInfoSequence
    ) {
        BillingItemTypes billingType = item.getBillingItemType();

        String mappingItemType = getMappingItemType(billingType);
        Long sourceId = getSourceId(item, billingType);

        WaseelItemMappingSetupDTO mapping = getWaseelMapping(mappingItemType, sourceId);

        String waseelItemType = safe(mapping.waseelItemType());

        List<Integer> itemSupportingInfoSequences = new ArrayList<>(existingSupportingInfoSequences);

        if (isMedicationCode(waseelItemType)) {
            Integer daysSupply = resolveMedicationDaysSupply(item);

            Integer daysSupplySequence = addDaysSupply(
                    supportingInfo,
                    supportingInfoSequence,
                    daysSupply
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
                itemSupportingInfoSequences
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
            List<Integer> supportingInfoSequences
    ) {
        Integer quantity = item.getQuantity() == null
                ? 1
                : item.getQuantity().intValue();

        BigDecimal quantityValue = BigDecimal.valueOf(quantity);
        BigDecimal unitPrice = money(item.getUnitPrice());
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

        return new WaseelApprovalItem(
                sequence,
                emptyToNull(type),
                emptyToNull(itemCode),
                emptyToNull(itemDescription),
                null,
                null,
                false,
                false,
                null,
                null,
                quantity,
                isMedicationCode(type) ? "package" : null,
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
                supportingInfoSequences == null || supportingInfoSequences.isEmpty()
                        ? List.of()
                        : supportingInfoSequences,
                List.of(1),
                List.of(1),
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
        return 30;
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
        if (type == BillingItemTypes.PROCEDURE) {
            return "PROCEDURE";
        }

        if (type == BillingItemTypes.SERVICE) {
            return "SERVICE";
        }

        if (type == BillingItemTypes.LABORATORY || type == BillingItemTypes.PATHOLOGY) {
            return "LABORATORY";
        }

        if (type == BillingItemTypes.RADIOLOGY) {
            return "RADIOLOGY";
        }

        if (type == BillingItemTypes.MEDICATION) {
            return "MEDICATION";
        }

        throw new RuntimeException("Unsupported billing item type for Waseel mapping: " + type);
    }

    private Long getSourceId(PatientServiceAndProduct item, BillingItemTypes type) {
        if (type == BillingItemTypes.PROCEDURE) {
            return item.getProcedureId();
        }

        if (type == BillingItemTypes.SERVICE) {
            return item.getServiceId();
        }

        if (type == BillingItemTypes.LABORATORY
                || type == BillingItemTypes.RADIOLOGY
                || type == BillingItemTypes.PATHOLOGY) {
            return item.getDiagnosticTestId();
        }

        if (type == BillingItemTypes.MEDICATION) {
            return item.getBrandMedicationId();
        }

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