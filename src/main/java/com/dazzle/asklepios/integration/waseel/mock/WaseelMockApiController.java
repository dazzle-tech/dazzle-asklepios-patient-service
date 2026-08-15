package com.dazzle.asklepios.integration.waseel.mock;

import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalResponse;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Waseel-compatible HTTP surface for manual Postman testing while {@code waseel.api.mock-enabled=true}.
 * Paths mirror the real Waseel pre-authorization submit/search endpoints.
 */
@RestController
@RequestMapping
@ConditionalOnProperty(name = "waseel.api.mock-enabled", havingValue = "true")
@RequiredArgsConstructor
public class WaseelMockApiController {

    private final WaseelPreAuthorizationMockService mockService;

    @PostMapping("/approvals/providers/{providerId}/approval/request")
    public ApprovalResponse submitApproval(
            @PathVariable String providerId,
            @RequestBody WaseelApprovalRequest request
    ) {
        return mockService.submitApproval(request);
    }

    @GetMapping("/nphies-rest-external/providers/{providerId}/external/approval")
    public PreAuthorizationSearchResponse searchApproval(
            @PathVariable String providerId,
            @RequestParam("requestId") Long requestId
    ) {
        return mockService.searchApproval(requestId);
    }

    @PostMapping("/approvals/providers/{providerId}/approval/cancel/request")
    public ApprovalResponse cancelApproval(
            @PathVariable String providerId,
            @RequestBody MapBody body
    ) {
        Long approvalRequestId = body != null && body.approvalRequestId() != null
                ? Long.valueOf(body.approvalRequestId())
                : null;
        if (approvalRequestId == null) {
            throw new IllegalArgumentException("approvalRequestId is required");
        }
        return mockService.cancelApproval(approvalRequestId);
    }

    public record MapBody(String approvalRequestId, String cancelReason) {}
}
