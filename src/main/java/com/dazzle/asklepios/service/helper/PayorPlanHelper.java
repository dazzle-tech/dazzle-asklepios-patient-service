package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.PayorPlanClient;
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

}
