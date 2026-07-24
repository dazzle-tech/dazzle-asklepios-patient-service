package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.PolicyAssignmentClient;
import com.dazzle.asklepios.client.setup.dto.PolicyAssignmentDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class PolicyAssignmentHelper {
    private final PolicyAssignmentClient policyAssignmentClient;

    public PolicyAssignmentHelper(PolicyAssignmentClient policyAssignmentClient) {
        this.policyAssignmentClient = policyAssignmentClient;
    }

    public PolicyAssignmentDTO getPolicyAssignment(Long policyAssignmentId) {
        PolicyAssignmentDTO policyAssignmentDTO;
        try {
            policyAssignmentDTO = policyAssignmentClient.getPolicyAssignment(policyAssignmentId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Policy Assignment not found: " + policyAssignmentId,
                    "PolicyAssignment",
                    "notfound"
            );
        }
        return policyAssignmentDTO;
    }
}
