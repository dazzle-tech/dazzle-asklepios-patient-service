package com.dazzle.asklepios.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PreApprovalLimitEvaluatorTest {

    @Test
    void fixedAmountPostsWhileTheVisitStaysWithinTheLimit() {
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "FIXED",
                new BigDecimal("150"),
                new BigDecimal("100"),
                new BigDecimal("50"),
                null
        )).isFalse();
    }

    @Test
    void fixedAmountRequiresApprovalWhenTheLineCrossesTheLimit() {
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "FIXED",
                new BigDecimal("150"),
                new BigDecimal("100"),
                new BigDecimal("60"),
                null
        )).isTrue();
    }

    @Test
    void percentageUsesTheCoverageLimitAsItsBase() {
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "PERCENTAGE",
                new BigDecimal("80"),
                new BigDecimal("700"),
                new BigDecimal("50"),
                new BigDecimal("1000")
        )).isFalse();

        assertThat(PreApprovalLimitEvaluator.exceeds(
                "PERCENTAGE",
                new BigDecimal("80"),
                new BigDecimal("700"),
                new BigDecimal("200"),
                new BigDecimal("1000")
        )).isTrue();
    }

    @Test
    void percentageWithoutACoverageLimitUsesTheRunningVisitTotal() {
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "PERCENTAGE",
                new BigDecimal("80"),
                BigDecimal.ZERO,
                new BigDecimal("100"),
                null
        )).isTrue();

        assertThat(PreApprovalLimitEvaluator.exceeds(
                "PERCENTAGE",
                new BigDecimal("100"),
                BigDecimal.ZERO,
                new BigDecimal("100"),
                null
        )).isFalse();
    }

    @Test
    void missingLimitOrEmptyLineDoesNotRequireApproval() {
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "FIXED",
                null,
                BigDecimal.ZERO,
                new BigDecimal("10"),
                null
        )).isFalse();
        assertThat(PreApprovalLimitEvaluator.exceeds(
                "FIXED",
                new BigDecimal("10"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        )).isFalse();
    }
}
