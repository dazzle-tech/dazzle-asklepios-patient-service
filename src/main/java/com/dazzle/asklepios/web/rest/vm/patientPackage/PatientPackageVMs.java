package com.dazzle.asklepios.web.rest.vm.patientPackage;

import com.dazzle.asklepios.domain.PatientPackage;
import com.dazzle.asklepios.domain.PatientPackageItem;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PatientPackageStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Request and response bodies for patient packages. */
public final class PatientPackageVMs {

    private PatientPackageVMs() {}

    /** Give a package to a patient in a visit; its items can be ordered in that visit only. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AssignVM(
            @NotNull Long patientId,
            @NotNull Long encounterId,
            @NotNull Long packageSetupId
    ) implements Serializable {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CancelVM(@Size(max = 500) String reason) implements Serializable {}

    public record ItemVM(
            Long id,
            Long packageSetupItemId,
            BillingItemTypes itemType,
            Long sourceId,
            String itemCode,
            String itemName,
            Integer quantityTotal,
            Integer quantityUsed,
            Integer quantityRemaining,
            Boolean isCategory,
            Integer maxRepeat,
            BigDecimal discountPercent
    ) implements Serializable {

        public static ItemVM ofEntity(PatientPackageItem e) {
            return new ItemVM(
                    e.getId(),
                    e.getPackageSetupItemId(),
                    e.getItemType(),
                    e.getSourceId(),
                    e.getItemCode(),
                    e.getItemName(),
                    e.getQuantityTotal(),
                    e.getQuantityUsed(),
                    e.getQuantityRemaining(),
                    e.getIsCategory(),
                    e.getMaxRepeat(),
                    e.getDiscountPercent()
            );
        }
    }

    public record ResponseVM(
            Long id,
            Long patientId,
            Long encounterId,
            Long packageSetupId,
            String packageCode,
            String packageName,
            BigDecimal packagePrice,
            PatientPackageStatus status,
            String cancelReason,
            String createdBy,
            Instant createdDate,
            Integer quantityTotal,
            Integer quantityUsed,
            List<ItemVM> items
    ) implements Serializable {

        public static ResponseVM ofEntity(PatientPackage p, List<PatientPackageItem> items) {
            List<ItemVM> lines = items.stream().map(ItemVM::ofEntity).toList();
            int total = items.stream().mapToInt(i -> i.getQuantityTotal() == null ? 0 : i.getQuantityTotal()).sum();
            int used = items.stream().mapToInt(i -> i.getQuantityUsed() == null ? 0 : i.getQuantityUsed()).sum();
            return new ResponseVM(
                    p.getId(),
                    p.getPatientId(),
                    p.getEncounterId(),
                    p.getPackageSetupId(),
                    p.getPackageCode(),
                    p.getPackageName(),
                    p.getPackagePrice(),
                    p.getStatus(),
                    p.getCancelReason(),
                    p.getCreatedBy(),
                    p.getCreatedDate(),
                    total,
                    used,
                    lines
            );
        }
    }
}
