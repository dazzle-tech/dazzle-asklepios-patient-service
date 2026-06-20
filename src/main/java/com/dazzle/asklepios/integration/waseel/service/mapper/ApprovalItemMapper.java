package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.integration.waseel.client.WaseelItemMappingClient;
import com.dazzle.asklepios.integration.waseel.client.dto.WaseelItemMappingSetupDTO;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class ApprovalItemMapper {

    private final WaseelItemMappingClient waseelItemMappingClient;

    public List<WaseelApprovalItem> toWaseelItems(
            List<PatientServiceAndProduct> items,
            BigDecimal patientSharePercent,
            PatientEncounter encounter
    ) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        AtomicInteger sequence = new AtomicInteger(1);

        LocalDate itemDate = encounter != null && encounter.getEncounterDate() != null
                ? encounter.getEncounterDate()
                : LocalDate.now();

        return items.stream()
                .filter(item -> Boolean.FALSE.equals(item.getIsBilled()))
                .map(item -> toWaseelItem(
                        item,
                        sequence.getAndIncrement(),
                        patientSharePercent,
                        itemDate
                ))
                .toList();
    }

    private WaseelApprovalItem toWaseelItem(
            PatientServiceAndProduct item,
            Integer sequence,
            BigDecimal patientSharePercent,
            LocalDate itemDate
    ) {
        BillingItemTypes billingType = item.getBillingItemType();

        String mappingItemType = getMappingItemType(billingType);
        Long sourceId = getSourceId(item, billingType);

        WaseelItemMappingSetupDTO mapping = getWaseelMapping(mappingItemType, sourceId);

        String waseelType = getWaseelItemType(billingType);

        return buildItem(
                item,
                sequence,
                waseelType,
                safe(mapping.sbsCode()),
                safe(mapping.sbsDescription()),
                patientSharePercent,
                itemDate
        );
    }

    private WaseelItemMappingSetupDTO getWaseelMapping(String itemType, Long sourceId) {
        if (itemType == null || sourceId == null) {
            throw new RuntimeException("Cannot map Waseel item. Item type or source ID is missing.");
        }

        try {
            return waseelItemMappingClient.getMappingByItem(itemType, sourceId);
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

    private String getWaseelItemType(BillingItemTypes type) {
        if (type == BillingItemTypes.RADIOLOGY) {
            return "imaging";
        }

        if (type == BillingItemTypes.LABORATORY || type == BillingItemTypes.PATHOLOGY) {
            return "laboratory";
        }

        if (type == BillingItemTypes.MEDICATION) {
            return "medication";
        }

        return "services";
    }

    private WaseelApprovalItem buildItem(
            PatientServiceAndProduct item,
            Integer sequence,
            String type,
            String itemCode,
            String itemDescription,
            BigDecimal patientSharePercent,
            LocalDate itemDate
    ) {
        Integer quantity = item.getQuantity() == null
                ? 1
                : item.getQuantity().intValue();

        BigDecimal unitPrice = money(item.getUnitPrice());
        BigDecimal discount = money(item.getDiscountAmount());
        BigDecimal tax = money(item.getTaxAmount());
        BigDecimal net = money(item.getTotalAmount());

        return new WaseelApprovalItem(
                sequence,
                emptyToEmpty(type),
                emptyToEmpty(itemCode),
                emptyToEmpty(itemDescription),
                "",
                "",
                false,
                false,
                "",
                "",
                quantity,
                "",
                unitPrice,
                discount,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                net,
                tax,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                itemDate,
                itemDate,
                List.of(),
                List.of(1),
                List.of(1),
                null,
                List.of()
        );
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String emptyToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}