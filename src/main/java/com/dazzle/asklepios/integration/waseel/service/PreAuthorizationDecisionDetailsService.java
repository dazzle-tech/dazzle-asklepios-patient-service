package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationItem;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.repository.PreAuthorizationItemRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * List responses: puts the Waseel decision on rejected and partially approved lines, so the patient
 * account can show why and ask for the patient's choice (pay cash / pay the difference / cancel).
 */
@Service
@RequiredArgsConstructor
public class PreAuthorizationDecisionDetailsService {

    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final PreAuthorizationItemRepository preAuthorizationItemRepository;

    public void attach(List<PatientServiceAndProduct> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<PatientServiceAndProduct> decided = items.stream()
                .filter(item -> item.getPreAuthorizationRequestId() != null)
                .filter(item -> item.getPreAuthorizationStatus() == PreAuthorizationStatus.REJECTED
                        || item.getPreAuthorizationStatus() == PreAuthorizationStatus.PARTIAL)
                .toList();
        if (decided.isEmpty()) {
            return;
        }

        Set<Long> requestIds = decided.stream()
                .map(PatientServiceAndProduct::getPreAuthorizationRequestId)
                .collect(Collectors.toSet());
        Map<Long, PreAuthorizationRequest> waseelRequests = preAuthorizationRequestRepository.findAllById(requestIds).stream()
                .filter(request -> request.getApprovalRequestId() != null)
                .collect(Collectors.toMap(PreAuthorizationRequest::getId, Function.identity()));

        for (PatientServiceAndProduct item : decided) {
            if (!waseelRequests.containsKey(item.getPreAuthorizationRequestId())) {
                continue;
            }
            PreAuthorizationItemMatcher
                    .find(preAuthorizationItemRepository.findByPreAuthorizationIdOrderBySequenceAsc(item.getPreAuthorizationRequestId()), item)
                    .ifPresent(requestLine -> describe(item, requestLine));
        }
    }

    private static void describe(PatientServiceAndProduct item, PreAuthorizationItem requestLine) {
        if (item.getPreAuthorizationStatus() == PreAuthorizationStatus.REJECTED) {
            item.setPreAuthorizationReason(requestLine.getReasonCodes() == null
                    ? "Rejected by the payer"
                    : "Rejected by the payer: " + requestLine.getReasonCodes());
            return;
        }
        BigDecimal approved = money(requestLine.getPayerShare());
        BigDecimal net = money(requestLine.getNet());
        item.setPreAuthorizationApprovedAmount(approved);
        item.setPreAuthorizationReason(
                "Partially approved: insurance pays " + approved.toPlainString()
                        + (net.signum() > 0 ? " of " + net.toPlainString() : "")
                        + (Objects.isNull(requestLine.getReasonCodes()) ? "" : " (" + requestLine.getReasonCodes() + ")")
        );
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
