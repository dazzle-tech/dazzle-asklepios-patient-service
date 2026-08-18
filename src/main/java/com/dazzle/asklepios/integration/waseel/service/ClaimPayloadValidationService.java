package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimValidationError;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimPreAuthorizationInfo;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimRequest;
import com.dazzle.asklepios.integration.waseel.service.mapper.WaseelFactorFormatter;
import com.dazzle.asklepios.integration.waseel.service.mapper.WaseelItemTypeNormalizer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ClaimPayloadValidationService {

    private static final Set<String> ALLOWED_ITEM_TYPES = Set.of(
            "TRANSPORTATION-SRCA",
            "IMAGING",
            "LABORATORY",
            "MEDICAL-DEVICES",
            "MEDICATION-CODES",
            "PROCEDURES",
            "SERVICES",
            "COSMETIC-CODES",
            "HERBAL-AND-VITAMIN-CODES",
            "NUTRITION-CODES"
    );

    /** Saudi SBS codes use a 5-digit root, e.g. 83600-00-00 or 11012-01-00. */
    private static final Pattern NPHIES_ITEM_CODE = Pattern.compile("^\\d{5}(-\\d{2}(-\\d{2})?)?$");

    public List<ClaimValidationError> validate(WaseelClaimRequest claim) {
        List<ClaimValidationError> errors = new ArrayList<>();
        if (claim == null) {
            errors.add(error("Claim", "Claim payload is missing.", "Claim Information"));
            return errors;
        }

        WaseelClaimPreAuthorizationInfo preAuth = claim.preAuthorizationInfo();
        if (preAuth != null) {
            LocalDate accountingPeriod = preAuth.accountingPeriod();
            if (accountingPeriod != null && !accountingPeriod.isBefore(LocalDate.now())) {
                errors.add(error(
                        "Claim Information - Accounting Period",
                        "Accounting Period should be in past.",
                        "Claim Information"
                ));
            }
        }

        List<WaseelApprovalItem> items = claim.items() == null ? List.of() : claim.items();
        for (WaseelApprovalItem item : items) {
            if (item == null) {
                continue;
            }

            int seq = item.sequence() == null ? 0 : item.sequence();
            String type = WaseelItemTypeNormalizer.normalize(item.type());
            if (type == null) {
                errors.add(error(
                        "Items - " + seq + " : Type",
                        "Please provide Type for Item[" + seq + "] from "
                                + ALLOWED_ITEM_TYPES + ".",
                        "Items"
                ));
            } else if (!ALLOWED_ITEM_TYPES.contains(type)) {
                errors.add(error(
                        "Items - " + seq + " : Type",
                        "Invalid Type [" + type + "] for Item[" + seq + "]. Allowed values: "
                                + ALLOWED_ITEM_TYPES + ".",
                        "Items"
                ));
            }

            Integer quantity = item.quantity() == null || item.quantity() <= 0 ? 1 : item.quantity();
            BigDecimal unitPrice = money(item.unitPrice());
            BigDecimal factor = WaseelFactorFormatter.format(item.factor());
            BigDecimal tax = money(item.tax());
            BigDecimal expectedNet = BigDecimal.valueOf(quantity)
                    .multiply(unitPrice)
                    .multiply(factor)
                    .add(tax)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal actualNet = money(item.net());
            if (actualNet.compareTo(expectedNet) != 0) {
                errors.add(error(
                        "Items - " + seq + " : Net",
                        "Incorrect Net calculation for Item[" + seq
                                + "]. Net = (Quantity x UnitPrice) x Factor + TaxAmount",
                        "Items"
                ));
            }

            String itemCode = blankToNull(item.itemCode());
            if (itemCode != null && !NPHIES_ITEM_CODE.matcher(itemCode).matches()) {
                errors.add(error(
                        "Items-Item - " + seq,
                        "Item with Code [" + itemCode + "] is out of NPHIES service list. "
                                + "Configure a valid SBS code in Waseel Item Mapping for this service.",
                        "Items"
                ));
            }
        }

        return errors;
    }

    private ClaimValidationError error(String code, String message, String section) {
        return new ClaimValidationError(code, message, section);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
