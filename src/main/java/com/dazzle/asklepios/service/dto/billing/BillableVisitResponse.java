package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BillableVisitResponse(

        Long encounterId,

        String encounterNumber,

        LocalDate encounterDate,

        Long departmentId,

        EncounterType encounterType,

        String coverageType,

        EncounterBillingStatus billingStatus,

        BillingChargeStatus chargeStatus,

        int serviceCount,

        BigDecimal netAmount,

        String currency,

        boolean hasFinalInvoice,

        boolean invoiceReady,

        boolean eligibleForBilling

) implements Serializable {
}
