package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.CancelReason;

public final class WaseelCancelReasonMapper {

    private WaseelCancelReasonMapper() {
    }

    public static String toWaseelCode(CancelReason cancelReason) {
        if (cancelReason == null) {
            return null;
        }

        return switch (cancelReason) {
            case SERVICE_NOT_PERFORMED -> "NP";
            case WRONG_INFORMATION -> "WI";
            case TRANSACTION_ALREADY_SUBMITTED -> "TAS";
        };
    }
}