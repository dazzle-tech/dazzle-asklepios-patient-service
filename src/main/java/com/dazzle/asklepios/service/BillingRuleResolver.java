package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.billing.BillingTrigger;

public interface BillingRuleResolver {

    ResolvedBillingRule resolve(PatientServiceAndProduct item);

    record ResolvedBillingRule(
            Long ruleId,
            BillingTrigger trigger
    ) {
    }
}