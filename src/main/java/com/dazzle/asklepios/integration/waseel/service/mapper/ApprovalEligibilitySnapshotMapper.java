package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalBeneficiary;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalCoverageClass;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEligibilitySnapshot;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalInsurancePlan;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApprovalEligibilitySnapshotMapper {

    private final ObjectMapper objectMapper;

    public WaseelApprovalEligibilitySnapshot fromEligibilityRequest(WaseelEligibilityRequest eligibilityRequest) {
        if (eligibilityRequest == null) {
            throw new BadRequestAlertException(
                    "Eligibility request is required",
                    "preAuthorization",
                    "eligibility.required"
            );
        }

        if (eligibilityRequest.getResponseJson() == null || eligibilityRequest.getResponseJson().isBlank()) {
            throw new BadRequestAlertException(
                    "Eligibility response JSON is required before pre-authorization",
                    "preAuthorization",
                    "eligibility.response.required"
            );
        }

        JsonNode root = parseJson(eligibilityRequest.getResponseJson());

        JsonNode beneficiaryNode = findNode(root, "beneficiary");
        JsonNode insurancePlanNode = findNode(root, "insurancePlan");

        if (beneficiaryNode == null || beneficiaryNode.isMissingNode() || beneficiaryNode.isNull()) {
            throw new BadRequestAlertException(
                    "Beneficiary data not found in eligibility response",
                    "preAuthorization",
                    "eligibility.beneficiary.notFound"
            );
        }

        if (insurancePlanNode == null || insurancePlanNode.isMissingNode() || insurancePlanNode.isNull()) {
            throw new BadRequestAlertException(
                    "Insurance plan data not found in eligibility response",
                    "preAuthorization",
                    "eligibility.insurancePlan.notFound"
            );
        }

        return new WaseelApprovalEligibilitySnapshot(
                eligibilityRequest.getTransfer() != null ? eligibilityRequest.getTransfer() : bool(root, "transfer"),
                bool(root, "isNewBorn"),
                toBeneficiary(beneficiaryNode),
                toInsurancePlan(insurancePlanNode),
                firstNonBlank(
                        eligibilityRequest.getEligibilityResponseId(),
                        text(root, "eligibilityResponseId")
                ),
                firstNonBlank(
                        eligibilityRequest.getEligibilityResponseUrl(),
                        text(root, "eligibilityResponseUrl")
                )
        );
    }

    private WaseelApprovalBeneficiary toBeneficiary(JsonNode node) {
        return new WaseelApprovalBeneficiary(
                text(node, "firstName"),
                text(node, "secondName"),
                text(node, "thirdName"),
                text(node, "familyName"),
                text(node, "fullName"),
                text(node, "fileId"),
                text(node, "dob"),
                text(node, "gender"),
                text(node, "documentType"),
                text(node, "documentId"),
                text(node, "eHealthId"),
                text(node, "nationality"),
                text(node, "residencyType"),
                text(node, "contactNumber"),
                text(node, "maritalStatus"),
                text(node, "occupation"),
                text(node, "bloodGroup"),
                text(node, "preferredLanguage"),
                text(node, "emergencyPhoneNumber"),
                text(node, "email"),
                text(node, "addressLine"),
                text(node, "streetLine"),
                text(node, "city"),
                text(node, "state"),
                text(node, "country"),
                text(node, "postalCode")
        );
    }

    private WaseelApprovalInsurancePlan toInsurancePlan(JsonNode node) {
        return new WaseelApprovalInsurancePlan(
                objectValue(node, "planId"),
                text(node, "payerId"),
                text(node, "memberCardId"),
                text(node, "policyNumber"),
                text(node, "policyHolder"),
                decimal(node, "maxLimit"),
                decimal(node, "patientShare"),
                text(node, "coverageType"),
                toCoverageClassList(node.get("coverageClass")),
                text(node, "relationWithSubscriber"),
                text(node, "expiryDate"),
                text(node, "payerName"),
                text(node, "payerNphiesId"),
                bool(node, "primary"),
                text(node, "tpaNphiesId")
        );
    }

    private List<WaseelApprovalCoverageClass> toCoverageClassList(JsonNode coverageClassNode) {
        List<WaseelApprovalCoverageClass> result = new ArrayList<>();

        if (coverageClassNode == null || coverageClassNode.isNull() || !coverageClassNode.isArray()) {
            return result;
        }

        for (JsonNode item : coverageClassNode) {
            result.add(new WaseelApprovalCoverageClass(
                    text(item, "type"),
                    text(item, "value"),
                    text(item, "name")
            ));
        }

        return result;
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BadRequestAlertException(
                    "Invalid eligibility response JSON",
                    "preAuthorization",
                    "eligibility.response.invalid"
            );
        }
    }

    private JsonNode findNode(JsonNode root, String fieldName) {
        if (root == null || root.isNull()) {
            return null;
        }

        if (root.has(fieldName)) {
            return root.get(fieldName);
        }

        if (root.has("data") && root.get("data").has(fieldName)) {
            return root.get("data").get(fieldName);
        }

        if (root.has("response") && root.get("response").has(fieldName)) {
            return root.get("response").get(fieldName);
        }

        if (root.has("body") && root.get("body").has(fieldName)) {
            return root.get("body").get(fieldName);
        }

        return root.findValue(fieldName);
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return "";
        }

        return node.get(fieldName).asText("");
    }

    private Boolean bool(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return false;
        }

        return node.get(fieldName).asBoolean(false);
    }

    private BigDecimal decimal(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }

        try {
            return new BigDecimal(node.get(fieldName).asText());
        } catch (Exception e) {
            return null;
        }
    }

    private Object objectValue(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);

        if (value.isBoolean()) {
            return value.asBoolean();
        }

        if (value.isLong() || value.isInt()) {
            return value.asLong();
        }

        if (value.isDouble() || value.isFloat() || value.isBigDecimal()) {
            return value.asDouble();
        }

        return value.asText();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }
}