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

        if (hasText(coverageType) || hasText(networkId)) {
            PayorPlanDTO matchedPlan = findPlanByCchiMatch(
                    payorId,
                    coverageType,
                    networkId,
                    policyClassName
            );

            if (hasId(matchedPlan)) {
                return matchedPlan.id();
            }
        }

        if (hasText(networkId)) {
            PayorPlanDTO planByNetwork = findPlanByWaseelPlanId(payorId, networkId.trim());
            if (hasId(planByNetwork)) {
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
        } catch (feign.FeignException ex) {
            if (isClientMiss(ex)) {
                return null;
            }
            throw ex;
        }
    }

    private PayorPlanDTO findPlanByWaseelPlanId(Long payorId, String waseelPlanId) {
        try {
            return payorPlanClient.getPayorPlanByWaseelPlanId(payorId, waseelPlanId);
        } catch (feign.FeignException ex) {
            if (isClientMiss(ex)) {
                return null;
            }
            throw ex;
        }
    }

    private static boolean hasId(PayorPlanDTO plan) {
        return plan != null && plan.id() != null && plan.id() > 0;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isClientMiss(feign.FeignException ex) {
        return ex instanceof feign.FeignException.NotFound
                || ex instanceof feign.FeignException.BadRequest
                || ex.status() == 404
                || ex.status() == 400;
    }
}
