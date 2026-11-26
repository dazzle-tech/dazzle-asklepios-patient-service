package com.dazzle.asklepios.service.event;

import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;

/**
 * A billing line was cancelled from the patient account (for example the patient declined a
 * rejected or partially approved service), so the order it came from may no longer be done.
 */
public record ServiceCancelledFromAccountEvent(
        ServiceSource serviceSource,
        Long sourceId,
        BillingItemTypes billingItemType,
        String reason
) {}
