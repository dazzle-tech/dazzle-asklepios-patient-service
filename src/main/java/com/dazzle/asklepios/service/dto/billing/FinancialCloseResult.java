package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;

import java.io.Serializable;
import java.time.Instant;

public record FinancialCloseResult(

        Long encounterId,

        EncounterBillingStatus billingStatus,

        Instant financiallyClosedAt,

        String financiallyClosedBy

) implements Serializable {
}
