package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalResponse;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCancelRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.request.PreAuthorizationCommunicationRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCancelResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCommunicationResponse;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchItem;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchItemDecision;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Local stand-in for Waseel pre-authorization APIs.
 *
 * <p>Submit stores the request and returns a Queued approval response (same shape as staging).
 * Search builds a dynamic response from {@link WaseelMockPreAuthStatus} so the full app flow
 * (submit → refresh/search → map status/items) can be exercised without calling Waseel.
 */
@Service
@Slf4j
public class WaseelPreAuthorizationMockService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final WaseelApiProperties properties;
    private final AtomicLong approvalRequestSeq = new AtomicLong(90_000_000L);
    private final AtomicLong approvalResponseSeq = new AtomicLong(98_000_000L);
    private final AtomicLong transactionSeq = new AtomicLong(57_000_000L);
    private final AtomicLong itemIdSeq = new AtomicLong(177_900_000L);
    private final AtomicLong itemDecisionSeq = new AtomicLong(141_000_000L);
    private final AtomicReference<WaseelMockPreAuthStatus> runtimeStatusOverride =
            new AtomicReference<>();
    private final Map<Long, MockSession> sessionsByApprovalRequestId = new ConcurrentHashMap<>();

    public WaseelPreAuthorizationMockService(WaseelApiProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isMockEnabled();
    }

    public WaseelMockPreAuthStatus currentStatus() {
        WaseelMockPreAuthStatus override = runtimeStatusOverride.get();
        if (override != null) {
            return override;
        }
        return WaseelMockPreAuthStatus.from(properties.mockPreAuthStatus());
    }

    /**
     * Optional runtime override (no restart). Pass null to clear and use YAML again.
     */
    public void setRuntimeStatus(WaseelMockPreAuthStatus status) {
        runtimeStatusOverride.set(status);
        log.info("[WASEEL_MOCK] Runtime pre-auth status set to {}", status);
    }

    public ApprovalResponse requestApproval(WaseelApprovalRequest request) {
        long approvalRequestId = approvalRequestSeq.incrementAndGet();
        long approvalResponseId = approvalResponseSeq.incrementAndGet();
        long transactionId = transactionSeq.incrementAndGet();
        long outgoingTransactionId = transactionSeq.incrementAndGet();

        sessionsByApprovalRequestId.put(
                approvalRequestId,
                new MockSession(approvalRequestId, approvalResponseId, request, false)
        );

        WaseelMockPreAuthStatus status = currentStatus();
        log.info(
                "[WASEEL_MOCK] Approval request stored. approvalRequestId={} approvalResponseId={} "
                        + "configuredSearchStatus={} itemCount={}",
                approvalRequestId,
                approvalResponseId,
                status.waseelStatus(),
                request != null && request.items() != null ? request.items().size() : 0
        );

        // Match real Waseel submit: always Queued; final decision comes from Search.
        return new ApprovalResponse(
                transactionId,
                "OK",
                "Processing Outcome: Queued",
                String.valueOf(outgoingTransactionId),
                approvalRequestId,
                approvalResponseId,
                null,
                "Queued",
                null,
                null,
                null
        );
    }

    public PreAuthorizationSearchResponse search(Long requestId) {
        MockSession session = sessionsByApprovalRequestId.get(requestId);
        WaseelMockPreAuthStatus status = currentStatus();

        if (session != null && session.cancelled()) {
            return buildCancelledSearch(session, status);
        }

        Long approvalRequestId = session != null ? session.approvalRequestId() : requestId;
        Long approvalResponseId = session != null
                ? session.approvalResponseId()
                : approvalResponseSeq.incrementAndGet();

        List<PreAuthorizationSearchItem> items = buildItems(session, status);
        BigDecimal paymentAmount = sumNet(items);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(3));

        String disposition = switch (status) {
            case APPROVED -> "All listed services are approved. (Waseel mock)";
            case REJECTED -> "All listed services are rejected. (Waseel mock)";
            case PARTIAL -> "Some services are approved and some are rejected. (Waseel mock)";
            case PENDED -> null;
        };

        String preAuthRefNo = status == WaseelMockPreAuthStatus.PENDED
                ? null
                : "MOCK-" + approvalRequestId;

        String processNotes = status == WaseelMockPreAuthStatus.PARTIAL
                ? "PARTIAL APPROVAL — last item rejected (Waseel mock)"
                : null;

        log.info(
                "[WASEEL_MOCK] Search response. requestId={} status={} outcome={} items={}",
                requestId,
                status.waseelStatus(),
                status.waseelOutcome(),
                items.size()
        );

        return new PreAuthorizationSearchResponse(
                approvalRequestId,
                approvalResponseId,
                null,
                null,
                "Waseel Mock Insurer",
                paymentAmount,
                "MOCK-" + approvalRequestId,
                status.waseelOutcome(),
                status.waseelStatus(),
                disposition,
                status == WaseelMockPreAuthStatus.PENDED ? null : "P14D",
                status == WaseelMockPreAuthStatus.PENDED ? null : now,
                status == WaseelMockPreAuthStatus.PENDED ? null : now.plusDays(14),
                processNotes,
                preAuthRefNo,
                transactionSeq.incrementAndGet(),
                now,
                null,
                null,
                items,
                null,
                null,
                null,
                null
        );
    }

    public PreAuthorizationCommunicationResponse communicate(
            PreAuthorizationCommunicationRequest request
    ) {
        log.info(
                "[WASEEL_MOCK] Communication accepted. claimResponseId={} payloadCount={}",
                request != null ? request.claimResponseId() : null,
                request != null && request.payloads() != null ? request.payloads().size() : 0
        );

        return new PreAuthorizationCommunicationResponse(
                transactionSeq.incrementAndGet(),
                "OK",
                "Communication accepted (Waseel mock)",
                "complete",
                "Mock communication processed",
                properties.providerId(),
                System.currentTimeMillis(),
                transactionSeq.incrementAndGet(),
                null
        );
    }

    public PreAuthorizationCancelResponse cancel(PreAuthorizationCancelRequest request) {
        Long approvalRequestId = request != null ? request.approvalRequestId() : null;
        if (approvalRequestId != null) {
            sessionsByApprovalRequestId.computeIfPresent(
                    approvalRequestId,
                    (id, session) -> new MockSession(
                            session.approvalRequestId(),
                            session.approvalResponseId(),
                            session.request(),
                            true
                    )
            );
        }

        log.info("[WASEEL_MOCK] Cancel accepted. approvalRequestId={}", approvalRequestId);

        return new PreAuthorizationCancelResponse(
                transactionSeq.incrementAndGet(),
                "OK",
                "Outcome for cancel request : cancelled.",
                transactionSeq.incrementAndGet(),
                "cancelled",
                OffsetDateTime.now(ZoneOffset.ofHours(3)),
                approvalRequestId,
                "",
                List.of()
        );
    }

    public ApprovalResponse cancelApproval(ApprovalCancelRequest request) {
        Long approvalRequestId = null;
        if (request != null && request.approvalRequestId() != null) {
            try {
                approvalRequestId = Long.valueOf(String.valueOf(request.approvalRequestId()));
            } catch (NumberFormatException ignored) {
                approvalRequestId = null;
            }
        }

        if (approvalRequestId != null) {
            sessionsByApprovalRequestId.computeIfPresent(
                    approvalRequestId,
                    (id, session) -> new MockSession(
                            session.approvalRequestId(),
                            session.approvalResponseId(),
                            session.request(),
                            true
                    )
            );
        }

        return new ApprovalResponse(
                transactionSeq.incrementAndGet(),
                "OK",
                "Outcome for cancel request : cancelled.",
                String.valueOf(transactionSeq.incrementAndGet()),
                approvalRequestId,
                null,
                null,
                "cancelled",
                null,
                "",
                List.of()
        );
    }

    private PreAuthorizationSearchResponse buildCancelledSearch(
            MockSession session,
            WaseelMockPreAuthStatus ignored
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(3));
        return new PreAuthorizationSearchResponse(
                session.approvalRequestId(),
                session.approvalResponseId(),
                null,
                null,
                "Waseel Mock Insurer",
                ZERO,
                "MOCK-" + session.approvalRequestId(),
                "cancelled",
                "cancelled",
                "Cancelled via Waseel mock",
                null,
                null,
                null,
                null,
                null,
                transactionSeq.incrementAndGet(),
                now,
                "cancelled",
                "Cancelled by provider (Waseel mock)",
                buildItems(session, WaseelMockPreAuthStatus.PENDED),
                null,
                null,
                null,
                null
        );
    }

    private List<PreAuthorizationSearchItem> buildItems(
            MockSession session,
            WaseelMockPreAuthStatus status
    ) {
        List<WaseelApprovalItem> sourceItems =
                session != null && session.request() != null && session.request().items() != null
                        ? session.request().items()
                        : List.of();

        if (sourceItems.isEmpty()) {
            sourceItems = List.of(
                    new WaseelApprovalItem(
                            1,
                            "services",
                            "83600-00-71",
                            "Mock consult service",
                            null,
                            null,
                            false,
                            false,
                            null,
                            null,
                            1,
                            null,
                            new BigDecimal("120"),
                            ZERO,
                            BigDecimal.ONE,
                            ZERO,
                            ZERO,
                            new BigDecimal("120"),
                            ZERO,
                            ZERO,
                            ZERO,
                            null,
                            null,
                            List.of(),
                            List.of(1),
                            List.of(),
                            null,
                            List.of()
                    )
            );
        }

        List<PreAuthorizationSearchItem> items = new ArrayList<>();
        int size = sourceItems.size();
        for (int i = 0; i < size; i++) {
            WaseelApprovalItem source = sourceItems.get(i);
            boolean rejectThis = switch (status) {
                case REJECTED -> true;
                case PARTIAL -> i == size - 1 && size > 1;
                default -> false;
            };
            boolean pending = status == WaseelMockPreAuthStatus.PENDED;

            String itemStatus = pending ? null : (rejectThis ? "rejected" : "approved");
            String reasonCodes = rejectThis ? "MN-1-1" : null;
            Long itemId = itemIdSeq.incrementAndGet();
            Integer sequence = source.sequence() != null ? source.sequence() : (i + 1);

            PreAuthorizationSearchItemDecision decision = pending
                    ? null
                    : new PreAuthorizationSearchItemDecision(
                            itemDecisionSeq.incrementAndGet(),
                            itemStatus,
                            sequence
                    );

            BigDecimal net = source.net() != null ? source.net() : ZERO;
            BigDecimal quantity = source.quantity() != null
                    ? BigDecimal.valueOf(source.quantity())
                    : BigDecimal.ONE;

            items.add(new PreAuthorizationSearchItem(
                    itemId,
                    sequence,
                    source.type(),
                    source.itemCode(),
                    source.itemDescription(),
                    source.nonStandardCode(),
                    source.nonStandardDesc(),
                    source.isPackage(),
                    source.isMaternity(),
                    quantity,
                    source.quantityCode(),
                    source.unitPrice() != null ? source.unitPrice() : ZERO,
                    source.discount() != null ? source.discount() : ZERO,
                    source.factor() != null ? source.factor() : BigDecimal.ONE,
                    source.taxPercent(),
                    source.tax(),
                    source.patientSharePercent(),
                    source.patientShare(),
                    source.payerShare(),
                    net,
                    itemStatus,
                    itemStatus,
                    reasonCodes,
                    decision
            ));
        }
        return items;
    }

    private static BigDecimal sumNet(List<PreAuthorizationSearchItem> items) {
        BigDecimal total = ZERO;
        for (PreAuthorizationSearchItem item : items) {
            if (item != null && item.net() != null) {
                total = total.add(item.net());
            }
        }
        return total;
    }

    private record MockSession(
            Long approvalRequestId,
            Long approvalResponseId,
            WaseelApprovalRequest request,
            boolean cancelled
    ) {}
}
