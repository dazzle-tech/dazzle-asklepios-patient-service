package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchItem;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchResponse;

import java.util.Locale;

final class PreAuthorizationWaseelStatusMapper {

    private PreAuthorizationWaseelStatusMapper() {}

    static PreAuthorizationStatus mapHeaderStatus(PreAuthorizationSearchResponse response) {
        if (response == null) {
            return PreAuthorizationStatus.PENDING_APPROVAL;
        }

        return mapStatusText(
                firstNonBlank(response.status(), response.outcome())
        );
    }

    static PreAuthorizationStatus mapItemStatus(PreAuthorizationSearchItem searchItem) {
        if (searchItem == null) {
            return PreAuthorizationStatus.PENDING_APPROVAL;
        }

        String decision = resolveItemDecisionText(searchItem);
        if (decision != null) {
            return mapStatusText(decision);
        }

        return PreAuthorizationStatus.PENDING_APPROVAL;
    }

    static String resolveItemDecisionText(PreAuthorizationSearchItem searchItem) {
        if (searchItem == null) {
            return null;
        }

        if (searchItem.itemDecision() != null
                && searchItem.itemDecision().status() != null
                && !searchItem.itemDecision().status().isBlank()) {
            return searchItem.itemDecision().status();
        }

        return firstNonBlank(searchItem.decision(), searchItem.status());
    }

    static boolean isPendingStatus(PreAuthorizationStatus status) {
        return status == PreAuthorizationStatus.PENDING_APPROVAL;
    }

    private static PreAuthorizationStatus mapStatusText(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return PreAuthorizationStatus.PENDING_APPROVAL;
        }

        String normalized = rawStatus.trim().toLowerCase(Locale.ROOT);

        if (normalized.contains("approved")
                || normalized.contains("complete")) {
            return PreAuthorizationStatus.APPROVED;
        }

        if (normalized.contains("rejected")
                || normalized.contains("denied")
                || normalized.contains("error")) {
            return PreAuthorizationStatus.REJECTED;
        }

        if (normalized.contains("partial")) {
            return PreAuthorizationStatus.PENDING_APPROVAL;
        }

        if (normalized.contains("pend")
                || normalized.contains("queue")
                || normalized.contains("processing")) {
            return PreAuthorizationStatus.PENDING_APPROVAL;
        }

        return PreAuthorizationStatus.PENDING_APPROVAL;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }
}
