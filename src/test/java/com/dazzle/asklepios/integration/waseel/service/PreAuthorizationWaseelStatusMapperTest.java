package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PreAuthorizationWaseelStatusMapperTest {

    @Test
    void technicalErrorKeepsTheLineHeldInsteadOfRejectingIt() {
        assertThat(PreAuthorizationWaseelStatusMapper.mapStatusAndOutcome("active", "error"))
                .isEqualTo(PreAuthorizationStatus.PENDING_APPROVAL);
        assertThat(PreAuthorizationWaseelStatusMapper.mapStatusAndOutcome("error", null))
                .isEqualTo(PreAuthorizationStatus.PENDING_APPROVAL);
    }

    @Test
    void statusWinsOverProcessingCompleteOutcome() {
        assertThat(PreAuthorizationWaseelStatusMapper.mapStatusAndOutcome("rejected", "Processing Complete"))
                .isEqualTo(PreAuthorizationStatus.REJECTED);
        assertThat(PreAuthorizationWaseelStatusMapper.mapStatusAndOutcome("partial", "Processing Complete"))
                .isEqualTo(PreAuthorizationStatus.PARTIAL);
        assertThat(PreAuthorizationWaseelStatusMapper.mapStatusAndOutcome("approved", "Processing Complete"))
                .isEqualTo(PreAuthorizationStatus.APPROVED);
        assertThat(PreAuthorizationWaseelStatusMapper.mapStatusAndOutcome("pended", "Queued"))
                .isEqualTo(PreAuthorizationStatus.PENDING_APPROVAL);
    }
}
