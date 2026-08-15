package com.dazzle.asklepios.integration.waseel.mock;

import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalResponse;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalInsurancePlan;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalRequest;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchItem;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchItemDecision;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory Waseel pre-authorization mock.
 * Activated via {@code waseel.api.mock-enabled=true}; production Waseel HTTP calls are bypassed
 * for submit/search/cancel while the rest of the integration stack stays unchanged.
 */
@Service
@Slf4j
public class WaseelPreAuthorizationMockService {

    private static final String REJECT_REASON_CODE = "MN-1-1";
    private static final String REJECT_REASON_TEXT =
            "Service is not clinically justified based on clinical practice guideline, "
                    + "without additional supporting diagnosis ";

    private final WaseelApiProperties properties;
    private final Map<Long, WaseelPreAuthorizationMockRecord> recordsByRequestId =
            new ConcurrentHashMap<>();

    private final AtomicLong approvalRequestIdSequence = new AtomicLong(39_365_000L);
    private final AtomicLong approvalResponseIdSequence = new AtomicLong(98_429_000L);
    private final AtomicLong transactionIdSequence = new AtomicLong(57_194_000L);
    private final AtomicLong outgoingTransactionIdSequence = new AtomicLong(11_800L);
    private final AtomicLong waseelItemIdSequence = new AtomicLong(177_910_000L);
    private final AtomicLong itemDecisionIdSequence = new AtomicLong(141_476_000L);
    private final AtomicLong preAuthRefSequence = new AtomicLong(48_253_454_992_023_180L);

    private volatile WaseelPreAuthorizationMockScenario defaultScenario =
            WaseelPreAuthorizationMockScenario.ROTATE;
    private volatile int rotateIndex = 0;

    public WaseelPreAuthorizationMockService(WaseelApiProperties properties) {
        this.properties = properties;
        if (properties.mockScenario() != null) {
            this.defaultScenario = WaseelPreAuthorizationMockScenario.fromConfig(properties.mockScenario());
        }
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(properties.mockEnabled());
    }

    public boolean hasRecord(Long approvalRequestId) {
        return approvalRequestId != null && recordsByRequestId.containsKey(approvalRequestId);
    }

    public WaseelPreAuthorizationMockScenario defaultScenario() {
        return defaultScenario;
    }

    public void setDefaultScenario(WaseelPreAuthorizationMockScenario scenario) {
        this.defaultScenario = scenario != null ? scenario : WaseelPreAuthorizationMockScenario.APPROVED;
        log.info("[WASEEL_MOCK] Default scenario set to {}", this.defaultScenario);
    }

    public void setScenarioForRequest(Long approvalRequestId, WaseelPreAuthorizationMockScenario scenario) {
        WaseelPreAuthorizationMockRecord record = recordsByRequestId.get(approvalRequestId);
        if (record == null) {
            throw new IllegalArgumentException(
                    "No mock pre-authorization found for approvalRequestId " + approvalRequestId
            );
        }
        record.setScenario(scenario != null ? scenario : WaseelPreAuthorizationMockScenario.APPROVED);
        log.info(
                "[WASEEL_MOCK] Scenario for approvalRequestId={} set to {}",
                approvalRequestId,
                record.scenario()
        );
    }

    public Map<Long, WaseelPreAuthorizationMockScenario> activeScenarios() {
        Map<Long, WaseelPreAuthorizationMockScenario> snapshot = new ConcurrentHashMap<>();
        recordsByRequestId.forEach((id, record) -> snapshot.put(id, record.scenario()));
        return snapshot;
    }

    public ApprovalResponse submitApproval(WaseelApprovalRequest request) {
        long approvalRequestId = approvalRequestIdSequence.incrementAndGet();
        long approvalResponseId = approvalResponseIdSequence.incrementAndGet();
        long transactionId = transactionIdSequence.incrementAndGet();
        String outgoingTransactionId = String.valueOf(outgoingTransactionIdSequence.incrementAndGet());

        WaseelPreAuthorizationMockScenario scenario = resolveScenarioForNewRequest();
        WaseelPreAuthorizationMockRecord record = new WaseelPreAuthorizationMockRecord(
                approvalRequestId,
                approvalResponseId,
                transactionId,
                outgoingTransactionId,
                request,
                OffsetDateTime.now(),
                scenario
        );
        recordsByRequestId.put(approvalRequestId, record);

        log.info(
                "[WASEEL_MOCK] Submit queued. approvalRequestId={} approvalResponseId={} scenario={} itemCount={}",
                approvalRequestId,
                approvalResponseId,
                scenario,
                request != null && request.items() != null ? request.items().size() : 0
        );

        return new ApprovalResponse(
                transactionId,
                "OK",
                "Processing Outcome: Queued",
                outgoingTransactionId,
                approvalRequestId,
                approvalResponseId,
                null,
                "Queued",
                null,
                null,
                null
        );
    }

    public PreAuthorizationSearchResponse searchApproval(Long approvalRequestId) {
        WaseelPreAuthorizationMockRecord record = recordsByRequestId.get(approvalRequestId);
        if (record == null) {
            throw new IllegalArgumentException(
                    "Mock pre-authorization not found for approvalRequestId " + approvalRequestId
            );
        }

        record.incrementSearchCount();
        if (record.cancelled()) {
            return buildCancelledSearchResponse(record);
        }

        if (record.searchCount() == 1) {
            log.info(
                    "[WASEEL_MOCK] Search #{} -> pended. approvalRequestId={}",
                    record.searchCount(),
                    approvalRequestId
            );
            return buildPendedSearchResponse(record);
        }

        log.info(
                "[WASEEL_MOCK] Search #{} -> {}. approvalRequestId={}",
                record.searchCount(),
                record.scenario(),
                approvalRequestId
        );
        return buildFinalSearchResponse(record);
    }

    public ApprovalResponse cancelApproval(Long approvalRequestId) {
        WaseelPreAuthorizationMockRecord record = recordsByRequestId.get(approvalRequestId);
        if (record != null) {
            record.markCancelled();
        }

        long transactionId = transactionIdSequence.incrementAndGet();
        String outgoingTransactionId = String.valueOf(outgoingTransactionIdSequence.incrementAndGet());

        log.info("[WASEEL_MOCK] Cancel accepted. approvalRequestId={}", approvalRequestId);

        return new ApprovalResponse(
                transactionId,
                "OK",
                "Outcome for cancel request : cancelled.",
                outgoingTransactionId,
                null,
                null,
                null,
                "cancelled",
                null,
                null,
                List.of()
        );
    }

    private WaseelPreAuthorizationMockScenario resolveScenarioForNewRequest() {
        if (defaultScenario != WaseelPreAuthorizationMockScenario.ROTATE) {
            return defaultScenario;
        }

        WaseelPreAuthorizationMockScenario[] cycle = {
                WaseelPreAuthorizationMockScenario.APPROVED,
                WaseelPreAuthorizationMockScenario.PARTIAL,
                WaseelPreAuthorizationMockScenario.REJECTED
        };
        WaseelPreAuthorizationMockScenario scenario = cycle[rotateIndex % cycle.length];
        rotateIndex++;
        return scenario;
    }

    private PreAuthorizationSearchResponse buildPendedSearchResponse(WaseelPreAuthorizationMockRecord record) {
        WaseelApprovalRequest request = record.request();
        List<WaseelApprovalItem> requestItems = request != null && request.items() != null
                ? request.items()
                : List.of();

        List<PreAuthorizationSearchItem> items = buildSearchItems(
                requestItems,
                WaseelPreAuthorizationMockScenario.APPROVED,
                false
        );

        BigDecimal paymentAmount = sumNet(requestItems);

        return new PreAuthorizationSearchResponse(
                record.approvalRequestId(),
                record.approvalResponseId(),
                payerNphiesId(request),
                memberCardId(request),
                insurerName(request),
                paymentAmount,
                claimResourceId(record.approvalRequestId()),
                "Queued",
                "pended",
                null,
                "P14D",
                record.submittedAt(),
                record.submittedAt().plusDays(14),
                null,
                null,
                record.transactionId(),
                OffsetDateTime.now(),
                null,
                null,
                items,
                null,
                null,
                null,
                null
        );
    }

    private PreAuthorizationSearchResponse buildFinalSearchResponse(WaseelPreAuthorizationMockRecord record) {
        WaseelApprovalRequest request = record.request();
        List<WaseelApprovalItem> requestItems = request != null && request.items() != null
                ? request.items()
                : List.of();

        WaseelPreAuthorizationMockScenario scenario = record.scenario();
        List<PreAuthorizationSearchItem> items = buildSearchItems(requestItems, scenario, true);

        String status;
        String disposition;
        BigDecimal paymentAmount;

        switch (scenario) {
            case APPROVED -> {
                status = "approved";
                disposition =
                        "All listed services are approved. however, the approval is below the pre-approval limit."
                                + "In this case, pre-approval is not required."
                                + "This approval is subject to CHI policy terms and conditions";
                paymentAmount = sumApprovedNet(items);
            }
            case PARTIAL -> {
                status = "partial";
                disposition = null;
                paymentAmount = sumApprovedNet(items);
            }
            case REJECTED -> {
                status = "rejected";
                disposition = null;
                paymentAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            default -> {
                status = "approved";
                disposition = null;
                paymentAmount = sumApprovedNet(items);
            }
        }

        String preAuthRefNo = String.valueOf(preAuthRefSequence.incrementAndGet());

        return new PreAuthorizationSearchResponse(
                record.approvalRequestId(),
                record.approvalResponseId(),
                payerNphiesId(request),
                memberCardId(request),
                insurerName(request),
                paymentAmount,
                claimResourceId(record.approvalRequestId()),
                "Processing Complete",
                status,
                disposition,
                scenario == WaseelPreAuthorizationMockScenario.PARTIAL ? "P30D" : "P14D",
                record.submittedAt(),
                record.submittedAt().plusDays(
                        scenario == WaseelPreAuthorizationMockScenario.PARTIAL ? 30 : 14
                ),
                scenario == WaseelPreAuthorizationMockScenario.PARTIAL
                        ? "APPROVED ANC SERVICES AS PER MOH PROGRAM  Except Toxo Plasma"
                        : null,
                preAuthRefNo,
                record.transactionId(),
                OffsetDateTime.now(),
                null,
                null,
                items,
                null,
                null,
                null,
                null
        );
    }

    private PreAuthorizationSearchResponse buildCancelledSearchResponse(WaseelPreAuthorizationMockRecord record) {
        PreAuthorizationSearchResponse finalResponse = buildFinalSearchResponse(record);
        return new PreAuthorizationSearchResponse(
                finalResponse.approvalRequestId(),
                finalResponse.approvalResponseId(),
                finalResponse.payerNphiesId(),
                finalResponse.memberCardId(),
                finalResponse.insurer(),
                finalResponse.paymentAmount(),
                finalResponse.claimResourceId(),
                "cancelled",
                "cancelled",
                finalResponse.disposition(),
                finalResponse.period(),
                finalResponse.preAuthStartDate(),
                finalResponse.preAuthEndDate(),
                finalResponse.processNotes(),
                finalResponse.preAuthRefNo(),
                record.transactionId(),
                OffsetDateTime.now(),
                "cancelled",
                "NP",
                finalResponse.item(),
                finalResponse.diagnosis(),
                finalResponse.careTeam(),
                finalResponse.supportingInfo(),
                finalResponse.errors()
        );
    }

    private List<PreAuthorizationSearchItem> buildSearchItems(
            List<WaseelApprovalItem> requestItems,
            WaseelPreAuthorizationMockScenario scenario,
            boolean applyDecision
    ) {
        if (requestItems.isEmpty()) {
            return List.of();
        }

        List<PreAuthorizationSearchItem> items = new ArrayList<>();
        int lastIndex = requestItems.size() - 1;

        for (int index = 0; index < requestItems.size(); index++) {
            WaseelApprovalItem requestItem = requestItems.get(index);
            if (requestItem == null) {
                continue;
            }

            String itemStatus = "approved";
            String reasonCodes = null;

            if (applyDecision) {
                switch (scenario) {
                    case REJECTED -> {
                        itemStatus = "rejected";
                        reasonCodes = REJECT_REASON_CODE;
                    }
                    case PARTIAL -> {
                        if (index == lastIndex && requestItems.size() > 1) {
                            itemStatus = "rejected";
                            reasonCodes = REJECT_REASON_CODE;
                        }
                    }
                    default -> itemStatus = "approved";
                }
            }

            Long waseelItemId = waseelItemIdSequence.incrementAndGet();
            Long itemDecisionId = itemDecisionIdSequence.incrementAndGet();
            Integer sequence = requestItem.sequence() != null ? requestItem.sequence() : index + 1;
            BigDecimal quantity = requestItem.quantity() != null
                    ? BigDecimal.valueOf(requestItem.quantity())
                    : BigDecimal.ONE;

            PreAuthorizationSearchItemDecision itemDecision = applyDecision
                    ? new PreAuthorizationSearchItemDecision(itemDecisionId, itemStatus, sequence)
                    : null;

            items.add(new PreAuthorizationSearchItem(
                    waseelItemId,
                    sequence,
                    requestItem.type(),
                    requestItem.itemCode(),
                    requestItem.itemDescription(),
                    requestItem.nonStandardCode(),
                    requestItem.nonStandardDesc(),
                    requestItem.isPackage(),
                    requestItem.isMaternity(),
                    quantity,
                    requestItem.quantityCode(),
                    money(requestItem.unitPrice()),
                    money(requestItem.discount()),
                    money(requestItem.factor(), BigDecimal.ONE),
                    requestItem.taxPercent(),
                    money(requestItem.tax()),
                    requestItem.patientSharePercent(),
                    money(requestItem.patientShare()),
                    money(requestItem.payerShare()),
                    money(requestItem.net()),
                    applyDecision ? itemStatus : null,
                    applyDecision ? itemStatus : null,
                    reasonCodes,
                    itemDecision
            ));
        }

        return items;
    }

    private static BigDecimal sumNet(List<WaseelApprovalItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (WaseelApprovalItem item : items) {
            if (item != null && item.net() != null) {
                total = total.add(item.net());
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumApprovedNet(List<PreAuthorizationSearchItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (PreAuthorizationSearchItem item : items) {
            if (item == null || item.net() == null) {
                continue;
            }
            String decision = item.itemDecision() != null ? item.itemDecision().status() : item.status();
            if (decision != null && decision.toLowerCase().contains("reject")) {
                continue;
            }
            total = total.add(item.net());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return money(value, BigDecimal.ZERO);
    }

    private static BigDecimal money(BigDecimal value, BigDecimal defaultValue) {
        if (value == null) {
            return defaultValue.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String claimResourceId(Long approvalRequestId) {
        String day = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy"));
        return day + "-" + approvalRequestId;
    }

    private static String payerNphiesId(WaseelApprovalRequest request) {
        WaseelApprovalInsurancePlan plan = request != null ? request.insurancePlan() : null;
        return plan != null ? plan.payerNphiesId() : null;
    }

    private static String memberCardId(WaseelApprovalRequest request) {
        WaseelApprovalInsurancePlan plan = request != null ? request.insurancePlan() : null;
        return plan != null ? plan.memberCardId() : null;
    }

    private static String insurerName(WaseelApprovalRequest request) {
        WaseelApprovalInsurancePlan plan = request != null ? request.insurancePlan() : null;
        if (plan != null && plan.payerName() != null && !plan.payerName().isBlank()) {
            return plan.payerName();
        }
        return "Mock Insurance Payer";
    }

}
