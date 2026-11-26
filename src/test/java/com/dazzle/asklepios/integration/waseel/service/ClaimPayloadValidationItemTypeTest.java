package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimValidationError;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimPreAuthorizationInfo;
import com.dazzle.asklepios.integration.waseel.dto.claim.WaseelClaimRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Sara's dental claim: the gingivectomy lines are ORAL-HEALTH-OP, which only a dental claim accepts. */
class ClaimPayloadValidationItemTypeTest {

    private final ClaimPayloadValidationService validation = new ClaimPayloadValidationService();

    @Test
    void aDentalClaimAcceptsOralHealthItems() {
        assertThat(typeErrors(claim("oral", "ORAL-HEALTH-OP"))).isEmpty();
    }

    @Test
    void aDentalClaimRejectsProceduresAsWaseelDoes() {
        assertThat(typeErrors(claim("oral", "PROCEDURES")))
                .singleElement()
                .satisfies(error -> assertThat(error.message()).contains("Invalid Type [PROCEDURES]").contains("ORAL-HEALTH-OP"));
    }

    @Test
    void aProfessionalClaimKeepsItsItemTypes() {
        assertThat(typeErrors(claim("professional", "PROCEDURES"))).isEmpty();
        assertThat(typeErrors(claim("professional", "ORAL-HEALTH-OP"))).hasSize(1);
    }

    private List<ClaimValidationError> typeErrors(WaseelClaimRequest claim) {
        return validation.validate(claim).stream()
                .filter(error -> error.code().endsWith(": Type"))
                .toList();
    }

    private static WaseelClaimRequest claim(String claimType, String itemType) {
        WaseelClaimPreAuthorizationInfo preAuthorizationInfo = new WaseelClaimPreAuthorizationInfo(
                null, claimType, "op", null, null, null, null, null, null, null, null, null, null, null, null, null);
        WaseelApprovalItem item = new WaseelApprovalItem(
                1, itemType, "97231-00-00", "Gingivectomy per quadrant", null, null, false, false, "53", null,
                1, null, new BigDecimal("150.00"), BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, null,
                new BigDecimal("150.00"), BigDecimal.ZERO, null, null, null, null,
                List.of(), List.of(), List.of(), null, null);
        return new WaseelClaimRequest(
                false, false, null, null, null, null, preAuthorizationInfo, List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null, null, List.of(item), new BigDecimal("150.00"),
                null, null, null, null, List.of());
    }
}
