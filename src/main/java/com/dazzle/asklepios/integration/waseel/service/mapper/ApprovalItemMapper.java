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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class ApprovalItemMapper {

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

        return items.stream()
                .filter(item -> Boolean.FALSE.equals(item.getIsBilled()))
                .map(item -> toWaseelItem(
                        item,
                        sequence.getAndIncrement(),
                        patientSharePercent,
                        itemDate,
                        supportingInfoSequences
                ))
                .toList();
    }

    private WaseelApprovalItem toWaseelItem(
            PatientServiceAndProduct item,
            Integer sequence,
            BigDecimal patientSharePercent,
            LocalDate itemDate,
            List<Integer> supportingInfoSequences
    ) {
        BillingItemTypes billingType = item.getBillingItemType();

        String mappingItemType = getMappingItemType(billingType);
        Long sourceId = getSourceId(item, billingType);

        WaseelItemMappingSetupDTO mapping = getWaseelMapping(mappingItemType, sourceId);

        return buildItem(
                item,
                sequence,
                safe(mapping.waseelItemType()),
                safe(mapping.sbsCode()),
                safe(mapping.sbsDescription()),
                patientSharePercent,
                itemDate,
                supportingInfoSequences
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

        BigDecimal unitPrice = money(item.getUnitPrice());
        BigDecimal discount = money(item.getDiscountAmount());
        BigDecimal tax = money(item.getTaxAmount());
        BigDecimal net = money(item.getTotalAmount());

        BigDecimal patientShare = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal payerShare = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal patientSharePercentValue = patientSharePercent == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : patientSharePercent.setScale(2, RoundingMode.HALF_UP);

        List<Integer> safeSupportingInfoSequences =
                supportingInfoSequences == null || supportingInfoSequences.isEmpty()
                        ? List.of()
                        : supportingInfoSequences;

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
                null,
                unitPrice,
                discount,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                patientSharePercentValue,
                net,
                tax,
                patientShare,
                payerShare,
                itemDate,
                itemDate,
                safeSupportingInfoSequences,
                List.of(1),
                List.of(1),
                null,
                List.of()
        );
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