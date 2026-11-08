package com.dazzle.asklepios.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PreApprovalLimitEvaluatorTest {

    @Test
    void fixedAmountLeavesAnItemUnderItsOwnLimit() {
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "FIXED",
                new BigDecimal("100"),
                new BigDecimal("99.99"),
                null
        )).isFalse();
    }

    @Test
    void fixedAmountIncludesAnItemThatEqualsTheLimit() {
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "FIXED",
                new BigDecimal("100"),
                new BigDecimal("100"),
                null
        )).isTrue();
    }

    @Test
    void fixedAmountIncludesAnItemAboveTheLimit() {
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "FIXED",
                new BigDecimal("50"),
                new BigDecimal("50.01"),
                null
        )).isTrue();
    }

    @Test
    void anotherItemDoesNotAddToThisItemsLimit() {
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "FIXED",
                new BigDecimal("150"),
                new BigDecimal("60"),
                null
        )).isFalse();
    }

    @Test
    void percentageUsesTheCoverageLimitAsItsBase() {
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "PERCENTAGE",
                new BigDecimal("80"),
                new BigDecimal("799"),
                new BigDecimal("1000")
        )).isFalse();

        assertThat(PreApprovalLimitEvaluator.exceeds(
                "PERCENTAGE",
                new BigDecimal("80"),
                new BigDecimal("800"),
                new BigDecimal("1000")
        )).isTrue();
    }

    @Test
    void percentageWithoutACoverageLimitUsesTheEnteredAmount() {
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "PERCENTAGE",
                new BigDecimal("100"),
                new BigDecimal("100"),
                null
        )).isTrue();

        assertThat(PreApprovalLimitEvaluator.exceeds(
                "PERCENTAGE",
                new BigDecimal("100"),
                new BigDecimal("99"),
                null
        )).isFalse();
    }

    @Test
    void missingLimitOrEmptyLineDoesNotRequireApproval() {
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "FIXED",
                null,
                new BigDecimal("10"),
                null
        )).isFalse();
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "FIXED",
                new BigDecimal("10"),
                BigDecimal.ZERO,
                null
        )).isFalse();
    }
}
