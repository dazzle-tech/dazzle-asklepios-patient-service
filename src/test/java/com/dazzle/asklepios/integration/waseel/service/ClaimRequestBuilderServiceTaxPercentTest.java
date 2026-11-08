package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingPricingSnapshot;
import com.dazzle.asklepios.domain.enumeration.billing.TaxType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaimRequestBuilderServiceTaxPercentTest {

    @Test
    void usesTheLatestActiveSnapshotOfEachChargeLine() {
        Map<Long, BigDecimal> rates = ClaimRequestBuilderService.taxPercentByChargeLine(List.of(
                snapshot(1L, TaxType.PERCENTAGE, "15.000000"),
                snapshot(1L, TaxType.PERCENTAGE, "5.000000")
        ));

        assertThat(rates.get(1L)).isEqualByComparingTo("15");
    }

    @Test
    void lineWithoutPercentageTaxHasZeroRate() {
        Map<Long, BigDecimal> rates = ClaimRequestBuilderService.taxPercentByChargeLine(List.of(
                snapshot(2L, null, "15.000000")
        ));

        assertThat(rates.get(2L)).isEqualByComparingTo("0");
    }

    private BillingPricingSnapshot snapshot(Long chargeLineId, TaxType taxType, String rate) {
        BillingChargeLine chargeLine = mock(BillingChargeLine.class);
        when(chargeLine.getId()).thenReturn(chargeLineId);
        BillingPricingSnapshot snapshot = mock(BillingPricingSnapshot.class);
        when(snapshot.getChargeLine()).thenReturn(chargeLine);
        org.mockito.Mockito.lenient().when(snapshot.getTaxType()).thenReturn(taxType);
        org.mockito.Mockito.lenient().when(snapshot.getTaxRate()).thenReturn(new BigDecimal(rate));
        return snapshot;
    }
}
