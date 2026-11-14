package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchItem;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchItemDecision.Category;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The payer decision on one item of a Waseel pre-authorization, with its amounts:
 * <ul>
 *   <li>approved in full: the approved amount ("benefit") covers what was submitted;</li>
 *   <li>partial: approved for less than submitted, the patient pays the difference or cancels;</li>
 *   <li>rejected: nothing approved (or "rejected"), the patient pays it as cash or cancels;</li>
 *   <li>pending: no decision yet.</li>
 * </ul>
 * Example (partial response, item 7): rejected, submitted 332.50, benefit 0, reason
 * "MN-1-1: Service is not clinically justified ...".
 */
public record WaseelItemDecision(
        PreAuthorizationStatus status,
        String decisionText,
        BigDecimal submitted,
        BigDecimal approvedAmount,
        BigDecimal approvedInsuranceAmount,
        String reason
) {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    public static WaseelItemDecision of(PreAuthorizationSearchItem item) {
        String decisionText = PreAuthorizationWaseelStatusMapper.resolveItemDecisionText(item);
        PreAuthorizationStatus decided = PreAuthorizationWaseelStatusMapper.mapItemStatus(item);
        List<Category> categories = item == null || item.itemDecision() == null
                || item.itemDecision().claimItemDecisionCategories() == null
                ? List.of()
                : item.itemDecision().claimItemDecisionCategories();

        BigDecimal submitted = firstNonNull(value(categories, "submitted"), item == null ? null : item.net());
        BigDecimal benefit = value(categories, "benefit");
        BigDecimal approvedQuantity = value(categories, "approved-quantity");
        BigDecimal patientShare = firstNonNull(value(categories, "patientshare"), value(categories, "copay"));
        String reason = reason(categories);

        PreAuthorizationStatus status = resolveStatus(decided, submitted, benefit, approvedQuantity);
        BigDecimal approvedInsurance = benefit == null
                ? null
                : money(benefit.subtract(patientShare == null ? BigDecimal.ZERO : patientShare).max(BigDecimal.ZERO));

        return new WaseelItemDecision(status, decisionText, money(submitted), money(benefit), approvedInsurance, reason);
    }

    /** An approved item worth less than submitted is a partial approval; one with nothing approved is a rejection. */
    private static PreAuthorizationStatus resolveStatus(
            PreAuthorizationStatus decided,
            BigDecimal submitted,
            BigDecimal benefit,
            BigDecimal approvedQuantity
    ) {
        if (decided != PreAuthorizationStatus.APPROVED && decided != PreAuthorizationStatus.PARTIAL) {
            return decided;
        }
        boolean submittedSomething = submitted != null && submitted.signum() > 0;
        boolean nothingApproved = benefit != null && benefit.signum() == 0
                && (approvedQuantity == null || approvedQuantity.signum() == 0);
        if (submittedSomething && nothingApproved) {
            return PreAuthorizationStatus.REJECTED;
        }
        if (submittedSomething && benefit != null && submitted.subtract(benefit).compareTo(TOLERANCE) > 0) {
            return PreAuthorizationStatus.PARTIAL;
        }
        return decided;
    }

    private static BigDecimal value(List<Category> categories, String name) {
        return categories.stream()
                .filter(Objects::nonNull)
                .filter(category -> category.category() != null
                        && name.equals(category.category().trim().toLowerCase(Locale.ROOT)))
                .map(Category::categoryValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** "MN-1-1: Service is not clinically justified ..." from the categories that carry a reason. */
    private static String reason(List<Category> categories) {
        String text = categories.stream()
                .filter(Objects::nonNull)
                .filter(category -> isNotBlank(category.reasonCode()) || isNotBlank(category.reasonDescription()))
                .map(category -> isNotBlank(category.reasonCode())
                        ? category.reasonCode().trim() + (isNotBlank(category.reasonDescription())
                                ? ": " + category.reasonDescription().trim()
                                : "")
                        : category.reasonDescription().trim())
                .distinct()
                .collect(Collectors.joining("; "));
        return text.isEmpty() ? null : text;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal firstNonNull(BigDecimal first, BigDecimal second) {
        return first != null ? first : second;
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
