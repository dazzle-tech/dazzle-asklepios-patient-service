package com.dazzle.asklepios.service;

import com.dazzle.asklepios.service.dto.billing.PartyTaxDistribution;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Item: 100 SAR, tax 15% (15 SAR), patient copay 20%.
 * The existing coverage rules split the tax-inclusive net (115) into
 * patient 23 / insurance 92 before the toggles are applied.
 */
class BillingPricingServiceTaxDistributionTest {

    private static final BigDecimal TAX = amount("15");

    private final BillingPricingService service = new BillingPricingService();

    @Test
    void insuranceVisitBothTogglesOnTaxesBothShares() {
        PartyTaxDistribution result =
                service.distributeTax(TAX, amount("23"), amount("92"), true, true);

        assertDistribution(result, "3", "12", "23", "92");
        assertMoney("115", result.netAmount());
    }

    @Test
    void insuranceVisitPatientCopaymentOnlyTaxesPatientShare() {
        PartyTaxDistribution result =
                service.distributeTax(TAX, amount("23"), amount("92"), true, false);

        assertDistribution(result, "3", "0", "23", "80");
        assertMoney("103", result.netAmount());
    }

    @Test
    void insuranceVisitClaimShareOnlyTaxesInsuranceShare() {
        PartyTaxDistribution result =
                service.distributeTax(TAX, amount("23"), amount("92"), false, true);

        assertDistribution(result, "0", "12", "20", "92");
        assertMoney("112", result.netAmount());
    }

    @Test
    void insuranceVisitBothTogglesOffTaxesNoShare() {
        PartyTaxDistribution result =
                service.distributeTax(TAX, amount("23"), amount("92"), false, false);

        assertDistribution(result, "0", "0", "20", "80");
        assertMoney("100", result.netAmount());
    }

    @Test
    void zeroInsuranceShareKeepsTheWholeTaxOnThePatient() {
        PartyTaxDistribution result =
                service.distributeTax(TAX, amount("115"), BigDecimal.ZERO, true, true);

        assertDistribution(result, "15", "0", "115", "0");
    }

    @Test
    void zeroPatientShareKeepsTheWholeTaxOnTheInsurance() {
        PartyTaxDistribution result =
                service.distributeTax(TAX, BigDecimal.ZERO, amount("115"), true, true);

        assertDistribution(result, "0", "15", "0", "115");
    }

    @Test
    void zeroInsuranceShareNeverMovesTheRemovedPatientTaxToTheInsurance() {
        PartyTaxDistribution result =
                service.distributeTax(TAX, amount("115"), BigDecimal.ZERO, false, true);

        assertDistribution(result, "0", "0", "100", "0");
    }

    @Test
    void selfPayIgnoresClaimShare() {
        PartyTaxDistribution patientOff =
                service.distributeTax(TAX, amount("115"), BigDecimal.ZERO, false, true);
        PartyTaxDistribution bothOn =
                service.distributeTax(TAX, amount("115"), BigDecimal.ZERO, true, true);
        PartyTaxDistribution claimOff =
                service.distributeTax(TAX, amount("115"), BigDecimal.ZERO, true, false);
        PartyTaxDistribution bothOff =
                service.distributeTax(TAX, amount("115"), BigDecimal.ZERO, false, false);

        assertDistribution(patientOff, "0", "0", "100", "0");
        assertDistribution(bothOn, "15", "0", "115", "0");
        assertDistribution(claimOff, "15", "0", "115", "0");
        assertDistribution(bothOff, "0", "0", "100", "0");
    }

    @Test
    void missingTogglesKeepTheExistingBehaviour() {
        PartyTaxDistribution result =
                service.distributeTax(TAX, amount("23"), amount("92"), null, null);

        assertDistribution(result, "3", "12", "23", "92");
    }

    @Test
    void differentCopayAndTaxRates() {
        // 250 SAR, tax 5% = 12.5, copay 30% of 262.5 = 78.75
        PartyTaxDistribution result =
                service.distributeTax(
                        amount("12.5"),
                        amount("78.75"),
                        amount("183.75"),
                        true,
                        false
                );

        assertDistribution(result, "3.75", "0", "78.75", "175");
        assertMoney("253.75", result.netAmount());
    }

    @Test
    void roundingResidueStaysWithinTheCalculatedTax() {
        // 3 items' worth of odd values: shares 33.3333 / 66.6667, tax 10.0001
        PartyTaxDistribution result =
                service.distributeTax(
                        amount("10.0001"),
                        amount("33.3333"),
                        amount("66.6667"),
                        true,
                        true
                );

        assertMoney(
                "10.0001",
                result.patientTaxAmount().add(result.insuranceTaxAmount())
        );
        assertMoney("100", result.netAmount());
    }

    private static void assertDistribution(
            PartyTaxDistribution result,
            String patientTax,
            String insuranceTax,
            String patientAmount,
            String insuranceAmount
    ) {
        assertMoney(patientTax, result.patientTaxAmount());
        assertMoney(insuranceTax, result.insuranceTaxAmount());
        assertMoney(patientAmount, result.patientAmount());
        assertMoney(insuranceAmount, result.insuranceAmount());
    }

    private static void assertMoney(
            String expected,
            BigDecimal actual
    ) {
        assertEquals(
                0,
                amount(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual
        );
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
