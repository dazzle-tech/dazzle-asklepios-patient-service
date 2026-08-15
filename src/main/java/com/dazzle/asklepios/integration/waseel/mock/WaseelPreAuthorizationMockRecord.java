package com.dazzle.asklepios.integration.waseel.mock;

import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalRequest;

import java.time.OffsetDateTime;

final class WaseelPreAuthorizationMockRecord {

    private final Long approvalRequestId;
    private final Long approvalResponseId;
    private final Long transactionId;
    private final String outgoingTransactionId;
    private final WaseelApprovalRequest request;
    private final OffsetDateTime submittedAt;
    private WaseelPreAuthorizationMockScenario scenario;
    private int searchCount;
    private boolean cancelled;

    WaseelPreAuthorizationMockRecord(
            Long approvalRequestId,
            Long approvalResponseId,
            Long transactionId,
            String outgoingTransactionId,
            WaseelApprovalRequest request,
            OffsetDateTime submittedAt,
            WaseelPreAuthorizationMockScenario scenario
    ) {
        this.approvalRequestId = approvalRequestId;
        this.approvalResponseId = approvalResponseId;
        this.transactionId = transactionId;
        this.outgoingTransactionId = outgoingTransactionId;
        this.request = request;
        this.submittedAt = submittedAt;
        this.scenario = scenario;
    }

    Long approvalRequestId() {
        return approvalRequestId;
    }

    Long approvalResponseId() {
        return approvalResponseId;
    }

    Long transactionId() {
        return transactionId;
    }

    String outgoingTransactionId() {
        return outgoingTransactionId;
    }

    WaseelApprovalRequest request() {
        return request;
    }

    OffsetDateTime submittedAt() {
        return submittedAt;
    }

    WaseelPreAuthorizationMockScenario scenario() {
        return scenario;
    }

    void setScenario(WaseelPreAuthorizationMockScenario scenario) {
        this.scenario = scenario;
    }

    int searchCount() {
        return searchCount;
    }

    void incrementSearchCount() {
        this.searchCount++;
    }

    boolean cancelled() {
        return cancelled;
    }

    void markCancelled() {
        this.cancelled = true;
    }
}
