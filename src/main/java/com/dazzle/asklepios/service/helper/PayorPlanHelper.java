package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.PayorPlanClient;
import com.dazzle.asklepios.client.setup.dto.PayorPlanDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class PayorPlanHelper {

    private final PayorPlanClient payorPlanClient;

    public PayorPlanHelper(PayorPlanClient payorPlanClient) {
        this.payorPlanClient = payorPlanClient;
    }

    public void validatePayorPlanExists(Long payorPlanId) {
        try {
            payorPlanClient.existsPayorPlan(payorPlanId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "PayorPlan not found: " + payorPlanId,
                    "payorPlan",
                    "notfound"
            );
        }
    }

    public Long resolvePlanId(
            Long payorId,
            Long planId,
            String networkId,
            String policyClassName,
            String coverageType
    ) {
        if (planId != null && planId > 0) {
            validatePayorPlanExists(planId);
            return planId;
        }

        if (payorId == null || payorId <= 0) {
            return null;
        }

        PayorPlanDTO matchedPlan = findPlanByCchiMatch(
                payorId,
                coverageType,
                networkId,
                policyClassName
        );

        if (matchedPlan != null && matchedPlan.id() != null && matchedPlan.id() > 0) {
            return matchedPlan.id();
        }

        if (networkId != null && !networkId.isBlank()) {
            PayorPlanDTO planByNetwork = findPlanByWaseelPlanId(payorId, networkId.trim());
            if (planByNetwork != null && planByNetwork.id() != null && planByNetwork.id() > 0) {
                return planByNetwork.id();
            }
        }

        return null;
    }

    private PayorPlanDTO findPlanByCchiMatch(
            Long payorId,
            String coverageType,
            String networkId,
            String policyClassName
    ) {
        try {
            return payorPlanClient.getPayorPlanByCchiMatch(
                    payorId,
                    coverageType,
                    networkId,
                    policyClassName
            );
        } catch (feign.FeignException.NotFound ex) {
            return null;
        }
    }

    private PayorPlanDTO findPlanByWaseelPlanId(Long payorId, String waseelPlanId) {
        try {
            return payorPlanClient.getPayorPlanByWaseelPlanId(payorId, waseelPlanId);
        } catch (feign.FeignException.NotFound ex) {
            return null;
        }
    }

}
