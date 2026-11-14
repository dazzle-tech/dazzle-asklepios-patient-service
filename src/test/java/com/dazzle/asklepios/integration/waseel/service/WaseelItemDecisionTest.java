package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationItem;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchItem;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Item decisions in the shape Waseel returns them (approved / partial / pended responses),
 * trimmed to the decision part.
 */
class WaseelItemDecisionTest {

    private static final String PARTIAL_RESPONSE = """
            { "status": "partial", "outcome": "Processing Complete",
              "items": [
                { "sequence": 2, "type": "laboratory", "itemCode": "73050-26-20", "net": 38.0,
                  "itemDecision": { "status": "approved", "itemSequence": 2, "claimItemDecisionCategories": [
                    {"category": "approved-quantity", "categoryValue": 1},
                    {"category": "benefit", "categoryValue": 38.0},
                    {"category": "copay", "categoryValue": 7.6},
                    {"category": "eligible", "categoryValue": 38.0},
                    {"category": "patientshare", "categoryValue": 7.6},
                    {"category": "submitted", "categoryValue": 38.0} ] } },
                { "sequence": 7, "type": "laboratory", "itemCode": "73100-26-50", "net": 332.5,
                  "itemDecision": { "status": "rejected", "itemSequence": 7, "claimItemDecisionCategories": [
                    {"category": "approved-quantity", "categoryValue": 0},
                    {"category": "benefit", "categoryValue": 0.0, "reasonCode": "MN-1-1",
                     "reasonDescription": "Service is not clinically justified based on clinical practice guideline, without additional supporting diagnosis "},
                    {"category": "copay", "categoryValue": 66.5},
                    {"category": "submitted", "categoryValue": 332.5} ] } },
                { "sequence": 8, "type": "laboratory", "itemCode": "73100-23-50", "net": 142.5,
                  "itemDecision": { "status": "approved", "itemSequence": 8, "claimItemDecisionCategories": [
                    {"category": "approved-quantity", "categoryValue": 1},
                    {"category": "benefit", "categoryValue": 100.0},
                    {"category": "patientshare", "categoryValue": 20.0},
                    {"category": "submitted", "categoryValue": 142.5} ] } },
                { "sequence": 9, "type": "services", "itemCode": "83600-00-00", "net": 30.0 }
              ] }
            """;

    private final List<PreAuthorizationSearchItem> items = read(PARTIAL_RESPONSE).item();

    @Test
    void anItemApprovedInFullIsApproved() {
        WaseelItemDecision decision = WaseelItemDecision.of(items.get(0));

        assertThat(decision.status()).isEqualTo(PreAuthorizationStatus.APPROVED);
        assertThat(decision.approvedAmount()).isEqualByComparingTo("38.00");
        assertThat(decision.approvedInsuranceAmount()).isEqualByComparingTo("30.40");
        assertThat(decision.reason()).isNull();
    }

    @Test
    void aRejectedItemCarriesItsReason() {
        WaseelItemDecision decision = WaseelItemDecision.of(items.get(1));

        assertThat(decision.status()).isEqualTo(PreAuthorizationStatus.REJECTED);
        assertThat(decision.submitted()).isEqualByComparingTo("332.50");
        assertThat(decision.reason()).startsWith("MN-1-1: Service is not clinically justified");
    }

    @Test
    void anItemApprovedForLessThanSubmittedIsPartial() {
        WaseelItemDecision decision = WaseelItemDecision.of(items.get(2));

        assertThat(decision.status()).isEqualTo(PreAuthorizationStatus.PARTIAL);
        assertThat(decision.approvedAmount()).isEqualByComparingTo("100.00");
        assertThat(decision.approvedInsuranceAmount()).isEqualByComparingTo("80.00");
        assertThat(decision.submitted()).isEqualByComparingTo("142.50");
    }

    @Test
    void anItemWithoutADecisionIsPending() {
        assertThat(WaseelItemDecision.of(items.get(3)).status()).isEqualTo(PreAuthorizationStatus.PENDING_APPROVAL);
    }

    @Test
    void anApprovedItemWithNothingApprovedIsARejection() {
        PreAuthorizationSearchItem item = read("""
                { "items": [ { "sequence": 1, "net": 50.0, "itemDecision": { "status": "approved",
                  "claimItemDecisionCategories": [
                    {"category": "approved-quantity", "categoryValue": 0},
                    {"category": "benefit", "categoryValue": 0} ] } } ] }
                """).item().get(0);

        assertThat(WaseelItemDecision.of(item).status()).isEqualTo(PreAuthorizationStatus.REJECTED);
    }

    @Test
    void aZeroPricedItemApprovedStaysApproved() {
        PreAuthorizationSearchItem item = read("""
                { "items": [ { "sequence": 1, "net": 0.0, "itemDecision": { "status": "approved",
                  "claimItemDecisionCategories": [
                    {"category": "approved-quantity", "categoryValue": 1},
                    {"category": "benefit", "categoryValue": 0},
                    {"category": "submitted", "categoryValue": 0} ] } } ] }
                """).item().get(0);

        assertThat(WaseelItemDecision.of(item).status()).isEqualTo(PreAuthorizationStatus.APPROVED);
    }

    @Test
    void theRequestLineIsMatchedToItsBillingLine() {
        PreAuthorizationItem lab = PreAuthorizationItem.builder().diagnosticTestId(7L).build();
        PreAuthorizationItem consultation = PreAuthorizationItem.builder().serviceId(29272L).build();
        PatientServiceAndProduct consultationLine = PatientServiceAndProduct.builder().serviceId(29272L).build();
        PatientServiceAndProduct otherLine = PatientServiceAndProduct.builder().serviceId(1L).build();

        assertThat(PreAuthorizationItemMatcher.find(List.of(lab, consultation), consultationLine)).contains(consultation);
        assertThat(PreAuthorizationItemMatcher.find(List.of(lab, consultation), otherLine)).isEmpty();
        assertThat(PreAuthorizationItemMatcher.find(List.of(lab), otherLine)).contains(lab);
    }

    private static PreAuthorizationSearchResponse read(String json) {
        try {
            return new ObjectMapper().readValue(json, PreAuthorizationSearchResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
