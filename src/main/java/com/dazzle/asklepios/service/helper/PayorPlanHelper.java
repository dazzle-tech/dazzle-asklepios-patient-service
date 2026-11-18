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
            Long payerId,
            Long planId,
            String networkId,
            String policyClassName,
            String coverageType
    ) {
        if (planId != null && planId > 0) {
            validatePayorPlanExists(planId);
            return planId;
        }

        if (payerId == null || payerId <= 0) {
            return null;
        }

        if (hasText(coverageType) || hasText(networkId)) {
            PayorPlanDTO matchedPlan = findPlanByCchiMatch(
                    payerId,
                    coverageType,
                    networkId,
                    policyClassName
            );

            if (hasId(matchedPlan)) {
                return matchedPlan.id();
            }
        }

        if (hasText(networkId)) {
            PayorPlanDTO planByNetwork = findPlanByWaseelPlanId(payerId, networkId.trim());
            if (hasId(planByNetwork)) {
                return planByNetwork.id();
            }
        }

        return null;
    }

    private PayorPlanDTO findPlanByCchiMatch(
            Long payerId,
            String coverageType,
            String networkId,
            String policyClassName
    ) {
        try {
            return payorPlanClient.getPayorPlanByCchiMatch(
                    payerId,
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

    private PayorPlanDTO findPlanByWaseelPlanId(Long payerId, String waseelPlanId) {
        try {
            return payorPlanClient.getPayorPlanByWaseelPlanId(payerId, waseelPlanId);
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
