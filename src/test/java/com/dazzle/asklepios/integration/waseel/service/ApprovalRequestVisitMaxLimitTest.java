package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.service.VisitMaxLimitTracker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The patient share sent to Waseel never goes above what is left of the visit max limit. */
class ApprovalRequestVisitMaxLimitTest {

    @Test
    void theLinesShareWhatIsLeftAndThePayerCarriesTheRest() {
        List<WaseelApprovalItem> sent = ApprovalRequestBuilderService.capPatientShares(
                List.of(item(1, "250.00", "50.00", "200.00"), item(2, "100.00", "20.00", "80.00")),
                VisitMaxLimitTracker.withConsumed(new BigDecimal("100"), new BigDecimal("60"))
        );

        assertThat(sent.get(0).patientShare()).isEqualByComparingTo("40");
        assertThat(sent.get(0).payerShare()).isEqualByComparingTo("210");
        assertThat(sent.get(0).patientSharePercent()).isEqualByComparingTo("20");
        assertThat(sent.get(1).patientShare()).isEqualByComparingTo("0");
        assertThat(sent.get(1).payerShare()).isEqualByComparingTo("100");
        assertThat(sent.get(1).net()).isEqualByComparingTo("100");
    }

    @Test
    void sharesWithinTheLimitAreSentUnchanged() {
        List<WaseelApprovalItem> items = List.of(item(1, "250.00", "50.00", "200.00"));

        assertThat(ApprovalRequestBuilderService.capPatientShares(items, VisitMaxLimitTracker.of(new BigDecimal("100"))))
                .isEqualTo(items);
    }

    @Test
    void withoutAMaxLimitNothingChanges() {
        List<WaseelApprovalItem> items = List.of(item(1, "9000.00", "1800.00", "7200.00"));

        assertThat(ApprovalRequestBuilderService.capPatientShares(items, VisitMaxLimitTracker.unbounded())).isSameAs(items);
    }

    private static WaseelApprovalItem item(int sequence, String net, String patientShare, String payerShare) {
        return new WaseelApprovalItem(
                sequence, "procedures", "CODE-" + sequence, "Description", null, null, false, false, null, null,
                1, null, new BigDecimal(net), BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, new BigDecimal("20.00"),
                new BigDecimal(net), BigDecimal.ZERO, new BigDecimal(patientShare), new BigDecimal(payerShare),
                null, null, List.of(), List.of(1), List.of(1), null, List.of()
        );
    }
}
