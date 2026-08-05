package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.enumeration.billing.BillingEventType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingTrigger;
import org.springframework.stereotype.Component;

@Component
public class BillingTriggerMatcher {

    public boolean matches(
            BillingTrigger trigger,
            BillingEventType eventType
    ) {
        if (trigger == null || eventType == null) {
            return false;
        }

        return switch (trigger) {
            case ENCOUNTER_CREATED ->
                    eventType == BillingEventType.ENCOUNTER_CREATED;

            case TREATMENT_STARTED ->
                    eventType == BillingEventType.TREATMENT_STARTED;

            case ORDERED ->
                    eventType == BillingEventType.ITEM_ORDERED;

            case DISPENSED ->
                    eventType == BillingEventType.ITEM_DISPENSED;

            case SERVICE_COMPLETED ->
                    eventType == BillingEventType.SERVICE_COMPLETED;

            case CHECKOUT ->
                    eventType == BillingEventType.CHECKOUT;

            case MANUAL ->
                    eventType == BillingEventType.MANUAL;

            case DEBIT_NOTE ->
                    eventType == BillingEventType.DEBIT_NOTE;
        };
    }
}