package com.dazzle.asklepios.web.rest.errors;

import com.dazzle.asklepios.service.InsurancePriceListCoverageService;
import com.dazzle.asklepios.service.dto.billing.InsurancePriceListCoverageCheckResult;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 409 so the UI can show a confirm dialog instead of a generic validation toast.
 * Retry the same request with {@code X-Accept-Uncovered-As-Cash: true} or
 * {@code acceptUncoveredAsCash=true} to bill the item as cash.
 */
public class InsuranceItemNotCoveredException extends BadRequestAlertException {

    public static final String ERROR_KEY = "insurance.item.notInPriceList";

    public static final String ENTITY_NAME = "insuranceCoverage";

    public InsuranceItemNotCoveredException(InsurancePriceListCoverageCheckResult check) {
        super(
                ErrorConstants.DEFAULT_TYPE,
                check == null || check.warningMessage() == null
                        ? InsurancePriceListCoverageService.warningMessageFor(
                                check == null ? null : check.notCoveredReason()
                        )
                        : check.warningMessage(),
                ENTITY_NAME,
                ERROR_KEY,
                HttpStatus.CONFLICT
        );
        applyProperties(
                check == null
                        ? List.of()
                        : List.of(check)
        );
    }

    public InsuranceItemNotCoveredException(List<InsurancePriceListCoverageCheckResult> items) {
        super(
                ErrorConstants.DEFAULT_TYPE,
                buildMessage(items),
                ENTITY_NAME,
                ERROR_KEY,
                HttpStatus.CONFLICT
        );
        applyProperties(items);
    }

    private void applyProperties(List<InsurancePriceListCoverageCheckResult> items) {
        getBody().setProperty("requiresConfirmation", true);
        getBody().setProperty("billedAsCashIfConfirmed", true);
        getBody().setProperty(
                "notCoveredReason",
                resolveNotCoveredReason(items)
        );
        getBody().setProperty("items", items);

        if (items != null && items.size() == 1) {
            InsurancePriceListCoverageCheckResult check = items.get(0);
            getBody().setProperty("billingItemType", check.billingItemType());
            getBody().setProperty("sourceId", check.sourceId());
            getBody().setProperty("itemName", check.itemName());
            getBody().setProperty("itemCode", check.itemCode());
            getBody().setProperty("cashUnitPrice", check.cashUnitPrice());
            getBody().setProperty("currency", check.currency());
        }
    }

    private static String buildMessage(List<InsurancePriceListCoverageCheckResult> items) {
        if (items == null || items.isEmpty()) {
            return InsurancePriceListCoverageService.WARNING_MESSAGE;
        }
        if (items.size() == 1) {
            InsurancePriceListCoverageCheckResult check = items.get(0);
            return check.warningMessage() != null
                    ? check.warningMessage()
                    : InsurancePriceListCoverageService.warningMessageFor(
                            check.notCoveredReason()
                    );
        }
        return InsurancePriceListCoverageService.warningMessageFor(
                items.get(0).notCoveredReason()
        )
                + " ("
                + items.size()
                + " items)";
    }

    private static String resolveNotCoveredReason(
            List<InsurancePriceListCoverageCheckResult> items
    ) {
        if (items != null) {
            for (InsurancePriceListCoverageCheckResult item : items) {
                if (item != null
                        && InsurancePriceListCoverageService.ELIGIBILITY_NOT_IN_FORCE
                        .equals(item.notCoveredReason())) {
                    return InsurancePriceListCoverageService.ELIGIBILITY_NOT_IN_FORCE;
                }
            }
            if (!items.isEmpty() && items.get(0) != null
                    && items.get(0).notCoveredReason() != null) {
                return items.get(0).notCoveredReason();
            }
        }

        return InsurancePriceListCoverageService.NOT_IN_INSURANCE_PRICE_LIST;
    }
}
