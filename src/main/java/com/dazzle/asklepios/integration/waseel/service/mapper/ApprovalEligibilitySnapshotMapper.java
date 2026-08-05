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
    private final ApLovMapperService apLovMapperService;

    public WaseelApprovalEligibilitySnapshot fromEligibilityRequest(WaseelEligibilityRequest eligibilityRequest) {
        if (eligibilityRequest == null) {
            throw new BadRequestAlertException(
                    "Eligibility request is required",
                    "preAuthorization",
                    "eligibility.required"
            );
        }

        if (isBlank(eligibilityRequest.getResponseJson())) {
            throw new BadRequestAlertException(
                    "Eligibility response JSON is required before pre-authorization",
                    "preAuthorization",
                    "eligibility.response.required"
            );
        }

        JsonNode responseRoot = parseJson(
                eligibilityRequest.getResponseJson(),
                "Invalid eligibility response JSON",
                "eligibility.response.invalid"
        );

        JsonNode requestRoot = null;
        if (!isBlank(eligibilityRequest.getRequestJson())) {
            requestRoot = parseJson(
                    eligibilityRequest.getRequestJson(),
                    "Invalid eligibility request JSON",
                    "eligibility.request.invalid"
            );
        }

        JsonNode beneficiaryNode = findNode(requestRoot, "beneficiary");
        JsonNode insurancePlanNode = findNode(requestRoot, "insurancePlan");
        JsonNode coverageNode = firstCoverageNode(responseRoot);
        if (isMissing(beneficiaryNode)) {
            beneficiaryNode = findNode(responseRoot, "beneficiary");
        }

        if (isMissing(insurancePlanNode)) {
            insurancePlanNode = findNode(responseRoot, "insurancePlan");
        }

        if (isMissing(beneficiaryNode)) {
            throw new BadRequestAlertException(
                    "Beneficiary data not found in eligibility request/response",
                    "preAuthorization",
                    "eligibility.beneficiary.notFound"
            );
        }

        if (isMissing(insurancePlanNode)) {
            throw new BadRequestAlertException(
                    "Insurance plan data not found in eligibility request/response",
                    "preAuthorization",
                    "eligibility.insurancePlan.notFound"
            );
        }

        return new WaseelApprovalEligibilitySnapshot(
                eligibilityRequest.getTransfer() != null
                        ? eligibilityRequest.getTransfer()
                        : bool(responseRoot, "transfer"),
                firstBoolean(
                        bool(responseRoot, "isNewBorn"),
                        bool(requestRoot, "isNewBorn"),
                        bool(beneficiaryNode, "isNewBorn")
                ),
                toBeneficiary(beneficiaryNode),
                toInsurancePlan(insurancePlanNode, coverageNode),
                firstNonBlank(
                        text(insurancePlanNode, "memberCardId"),
                        text(responseRoot, "memberId"),
                        text(requestRoot, "memberId")
                ),
                eligibilityRequest.getPatientInsuranceId(),
                eligibilityRequest.getProviderId(),
                eligibilityRequest.getDestinationId(),
                firstNonBlank(
                        eligibilityRequest.getEligibilityResponseId(),
                        text(responseRoot, "eligibilityResponseId"),
                        text(responseRoot, "nphiesResponseId"),
                        text(requestRoot, "eligibilityResponseId")
                ),
                firstNonBlank(
                        eligibilityRequest.getEligibilityResponseUrl(),
                        text(responseRoot, "eligibilityResponseUrl"),
                        text(responseRoot, "eligibilityIdentifierUrl"),
                        text(requestRoot, "eligibilityResponseUrl")
                )
        );
    }

    private JsonNode firstCoverageNode(JsonNode responseRoot) {
        JsonNode coverages = findNode(responseRoot, "coverages");

        if (coverages != null && coverages.isArray() && !coverages.isEmpty()) {
            return coverages.get(0);
        }

        return null;
    }

    private WaseelApprovalBeneficiary toBeneficiary(JsonNode node) {
        String maritalStatusRaw = text(node, "maritalStatus");
        String occupationRaw = text(node, "occupation");

        // Map values to NPHIES format using ApLovMapperService
        String maritalStatusMapped = mapMaritalStatus(maritalStatusRaw);
        String occupationMapped = mapOccupation(occupationRaw);

        // Validate critical fields exist
        String firstName = nullIfBlank(text(node, "firstName"));
        String documentId = nullIfBlank(text(node, "documentId"));
        
        if (isBlank(firstName)) {
            throw new BadRequestAlertException(
                    "Beneficiary firstName is required",
                    "preAuthorization",
                    "eligibility.beneficiary.firstName.required"
            );
        }

        return new WaseelApprovalBeneficiary(
                firstName,
                nullIfBlank(text(node, "secondName")),
                nullIfBlank(text(node, "thirdName")),
                nullIfBlank(text(node, "familyName")),
                nullIfBlank(text(node, "fullName")),
                firstNonBlank(
                        text(node, "fileId"),
                        text(node, "beneficiaryFileId"),
                        "240600003"
                ),
                nullIfBlank(text(node, "dob")),
                nullIfBlank(text(node, "gender")),
                nullIfBlank(text(node, "documentType")),
                documentId,
                nullIfBlank(text(node, "eHealthId")),
                nullIfBlank(text(node, "nationality")),
                nullIfBlank(text(node, "residencyType")),
                nullIfBlank(text(node, "contactNumber")),
                maritalStatusMapped,
                firstNonBlank(occupationMapped, "business"),
                nullIfBlank(text(node, "bloodGroup")),
                nullIfBlank(text(node, "preferredLanguage")),
                nullIfBlank(text(node, "emergencyPhoneNumber")),
                nullIfBlank(text(node, "email")),
                nullIfBlank(text(node, "addressLine")),
                nullIfBlank(text(node, "streetLine")),
                nullIfBlank(text(node, "city")),
                nullIfBlank(text(node, "state")),
                nullIfBlank(text(node, "country")),
                nullIfBlank(text(node, "postalCode"))
        );
    }

    private String mapMaritalStatus(String maritalStatusRaw) {
        if (isBlank(maritalStatusRaw)) {
            return null;
        }

        // Try to map assuming it's already a key
        String mappedValue = apLovMapperService.mapMaritalStatusKeyToNphies(maritalStatusRaw);
        if (!isBlank(mappedValue)) {
            return mappedValue;
        }

        // Try to map assuming it's a value code
        mappedValue = apLovMapperService.mapMaritalStatusValueCodeToNphies(maritalStatusRaw);
        if (!isBlank(mappedValue)) {
            return mappedValue;
        }

        // Try direct match with normalization for standard values
        String normalized = maritalStatusRaw.trim().toUpperCase();
        if (normalized.startsWith("M") || normalized.equals("MARRIED")) {
            return "M";
        } else if (normalized.startsWith("S") || normalized.equals("SINGLE") || normalized.equals("U")) {
            return "U";
        } else if (normalized.startsWith("D") || normalized.equals("DIVORCED")) {
            return "D";
        } else if (normalized.startsWith("W") || normalized.equals("WIDOWED")) {
            return "W";
        }

        // If value is already a valid NPHIES code (M, U, D, W), return it as-is
        String trimmed = maritalStatusRaw.trim().toUpperCase();
        if (trimmed.matches("[MUDW]")) {
            return trimmed;
        }

        // Default to null if unmapped
        return null;
    }

    private String mapOccupation(String occupationRaw) {
        if (isBlank(occupationRaw)) {
            return null;
        }

        String trimmedValue = occupationRaw.trim().toLowerCase();
        
        // Check if it's already a valid NPHIES occupation value
        String[] validValues = {"medical field", "skilled worker", "education", "agriculture", "business", "administration", "others"};
        for (String valid : validValues) {
            if (trimmedValue.equals(valid)) {
                return trimmedValue;
            }
        }

        // Check if it's "unknown" - map to null instead of "others" to let Waseel handle default
        if ("unknown".equalsIgnoreCase(trimmedValue)) {
            return null;
        }

        // Try to map assuming it's a key
        String mappedValue = apLovMapperService.mapOccupationKeyToNphies(occupationRaw);
        if (!isBlank(mappedValue)) {
            return mappedValue;
        }

        // Try to map assuming it's a value code
        mappedValue = apLovMapperService.mapOccupationValueCodeToNphies(occupationRaw);
        if (!isBlank(mappedValue)) {
            return mappedValue;
        }

        // If no mapping found, return null (don't default to "others")
        return null;
    }

    private WaseelApprovalInsurancePlan toInsurancePlan(JsonNode requestPlanNode, JsonNode coverageNode) {
        String tpaNphiesId = firstNonBlank(
                text(requestPlanNode, "tpaNphiesId"),
                text(coverageNode, "tpaNphiesId")
        );

        if (tpaNphiesId != null && (tpaNphiesId.equals("-1") || tpaNphiesId.isBlank())) {
            tpaNphiesId = null;
        }

        String memberCardId = firstNonBlank(
                text(coverageNode, "memberId"),
                text(requestPlanNode, "memberCardId")
        );

        String policyNumber = firstNonBlank(
                text(coverageNode, "policyNumber"),
                text(requestPlanNode, "policyNumber")
        );

        String policyHolder = firstNonBlank(
                text(coverageNode, "policyHolder"),
                text(requestPlanNode, "policyHolder"),
                text(requestPlanNode, "policyHolderName")
        );

        return new WaseelApprovalInsurancePlan(
                safePlanId(requestPlanNode, "planId"),
                firstNonBlank(text(requestPlanNode, "payerId"), text(requestPlanNode, "payerNphiesId"), "INS-FHIR"),
                memberCardId,
                policyNumber,
                policyHolder,
                decimal(requestPlanNode, "maxLimit"),
                decimal(requestPlanNode, "patientShare"),
                firstNonBlank(text(requestPlanNode, "coverageType"), "EHCPOL"),
                coverageClassFromResponseOrDefault(coverageNode, policyNumber),
                "self",
                firstNonBlank(
                        dateOnly(text(coverageNode, "benefitEndDate")),
                        text(requestPlanNode, "expiryDate")
                ),
                firstNonBlank(text(requestPlanNode, "payerName"), "Insurance Company Testing Payer"),
                firstNonBlank(text(requestPlanNode, "payerNphiesId"), text(requestPlanNode, "payerId"), "INS-FHIR"),
                true,
                tpaNphiesId
        );
    }

    private List<WaseelApprovalCoverageClass> coverageClassFromResponseOrDefault(
            JsonNode coverageNode,
            String policyNumber
    ) {
        List<WaseelApprovalCoverageClass> classes = new ArrayList<>();

        JsonNode classList = coverageNode == null ? null : coverageNode.get("classList");

        if (classList != null && classList.isArray()) {
            for (JsonNode item : classList) {
                classes.add(new WaseelApprovalCoverageClass(
                        text(item, "classType"),
                        text(item, "classValue"),
                        text(item, "className")
                ));
            }
        }

        if (!classes.isEmpty()) {
            return classes;
        }

        return List.of(
                new WaseelApprovalCoverageClass(
                        "plan",
                        policyNumber,
                        null
                )
        );
    }

    private String dateOnly(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        int index = value.indexOf("T");
        if (index > 0) {
            return value.substring(0, index);
        }

        return value;
    }

    private List<WaseelApprovalCoverageClass> coverageClassOrDefault(JsonNode node) {
        List<WaseelApprovalCoverageClass> coverageClasses =
                toCoverageClassList(node == null ? null : node.get("coverageClass"));

        if (coverageClasses != null && !coverageClasses.isEmpty()) {
            return coverageClasses;
        }

        String policyNumber = text(node, "policyNumber");

        if (isBlank(policyNumber)) {
            policyNumber = "17452394";
        }

        return List.of(
                new WaseelApprovalCoverageClass(
                        "plan",
                        policyNumber,
                        null
                )
        );
    }

    private Object safePlanId(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);

        // If it's a number, convert to string
        if (value.isLong() || value.isInt()) {
            long longVal = value.asLong();
            // Return null if the value is -1 (invalid marker)
            if (longVal == -1) {
                return null;
            }
            return String.valueOf(longVal);
        }

        if (value.isDouble() || value.isFloat() || value.isBigDecimal()) {
            long longVal = value.asLong();
            // Return null if the value is -1 (invalid marker)
            if (longVal == -1) {
                return null;
            }
            return String.valueOf(longVal);
        }

        // If it's a boolean, return null (invalid planId type)
        if (value.isBoolean()) {
            return null;
        }

        // Otherwise return as text
        String textValue = value.asText("");
        if (isBlank(textValue) || textValue.equals("-1")) {
            return null;
        }
        return textValue.trim();
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

    private JsonNode parseJson(String json, String title, String messageKey) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BadRequestAlertException(
                    title,
                    "preAuthorization",
                    messageKey
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

    private Boolean firstBoolean(Boolean... values) {
        if (values == null) {
            return false;
        }

        for (Boolean value : values) {
            if (Boolean.TRUE.equals(value)) {
                return true;
            }
        }

        return false;
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

    private boolean isMissing(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String nullIfBlank(String value) {
        return isBlank(value) ? null : value;
    }
}