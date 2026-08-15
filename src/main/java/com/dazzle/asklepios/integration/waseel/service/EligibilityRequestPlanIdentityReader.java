package com.dazzle.asklepios.integration.waseel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class EligibilityRequestPlanIdentityReader {

    private final ObjectMapper objectMapper;

    public EligibilityRequestPlanIdentityReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PlanIdentity read(
            String requestJson,
            String memberCardId,
            String policyNumber
    ) {
        JsonNode root = readTree(requestJson);
        if (root == null) {
            return PlanIdentity.empty();
        }

        JsonNode matched = matchPlan(root.path("insurancePlan"), memberCardId, policyNumber);
        if (matched == null) {
            JsonNode plans = root.path("beneficiary").path("plans");
            if (plans.isArray()) {
                for (JsonNode plan : plans) {
                    matched = matchPlan(plan, memberCardId, policyNumber);
                    if (matched != null) {
                        break;
                    }
                }
            }
        }

        if (matched == null && root.path("insurancePlan").isObject()) {
            matched = root.path("insurancePlan");
        }

        if (matched == null) {
            return PlanIdentity.empty();
        }

        return new PlanIdentity(
                text(matched, "memberCardId"),
                text(matched, "policyNumber"),
                text(matched, "policyHolder"),
                resolvePolicyClass(matched),
                parseDate(text(matched, "expiryDate")),
                text(matched, "payerName"),
                text(matched, "coverageType"),
                text(matched, "relationWithSubscriber")
        );
    }

    private JsonNode matchPlan(JsonNode plan, String memberCardId, String policyNumber) {
        if (plan == null || !plan.isObject()) {
            return null;
        }

        String member = clean(memberCardId);
        String policy = clean(policyNumber);
        String planMember = text(plan, "memberCardId");
        String planPolicy = text(plan, "policyNumber");

        if (member != null && member.equals(planMember)) {
            return plan;
        }
        if (policy != null && policy.equals(planPolicy)) {
            return plan;
        }
        return null;
    }

    private String resolvePolicyClass(JsonNode plan) {
        String direct = firstNonBlank(
                text(plan, "policyClassName"),
                text(plan, "policyClass")
        );
        if (direct != null) {
            return direct;
        }

        JsonNode classList = plan.path("coverageClassList");
        if (!classList.isArray()) {
            classList = plan.path("coverageClass");
        }
        if (classList.isArray()) {
            for (JsonNode classItem : classList) {
                String type = text(classItem, "type");
                if (type != null && "plan".equalsIgnoreCase(type)) {
                    return firstNonBlank(text(classItem, "name"), text(classItem, "value"));
                }
            }
            if (classList.size() > 0) {
                JsonNode first = classList.get(0);
                return firstNonBlank(text(first, "name"), text(first, "value"));
            }
        }
        return null;
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }
        return clean(node.path(field).asText());
    }

    private LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (value.length() >= 10) {
                return LocalDate.parse(value.substring(0, 10));
            }
            return LocalDate.parse(value);
        } catch (RuntimeException ignored) {
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    public record PlanIdentity(
            String memberCardId,
            String policyNumber,
            String policyHolder,
            String policyClassName,
            LocalDate expiryDate,
            String payerName,
            String coverageType,
            String relationWithSubscriber
    ) {
        static PlanIdentity empty() {
            return new PlanIdentity(null, null, null, null, null, null, null, null);
        }

        boolean isEmpty() {
            return memberCardId == null
                    && policyNumber == null
                    && policyHolder == null
                    && policyClassName == null
                    && expiryDate == null;
        }
    }
}
