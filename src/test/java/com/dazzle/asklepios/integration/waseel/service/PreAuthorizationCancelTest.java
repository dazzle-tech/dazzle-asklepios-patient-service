package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationCancelResponse;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Maha's case: the submission failed NPHIES validation (outcome "Error", BV-01265), so the cancel was
 * refused with RE-00133. The request still counts as cancelled, and its line waits for a new request.
 */
class PreAuthorizationCancelTest {

    private static final PreAuthorizationCancelResponse REFUSED = new PreAuthorizationCancelResponse(
            87930L, "OK", "Outcome for cancel request : Failed.", 52909216L, "Failed", null, null, "",
            List.of(Map.of("coding", List.of(Map.of(
                    "code", "RE-00133",
                    "display", "Task focus is not referring to a valid resource"))))
    );

    private static final PreAuthorizationCancelResponse ACCEPTED = new PreAuthorizationCancelResponse(
            1L, "OK", "Outcome for cancel request : Cancelled.", 2L, "cancelled", null, null, null, List.of()
    );

    @Test
    void aSubmissionStoppedByValidationNeverReachedThePayer() {
        assertThat(PreAuthorizationCancelOutcome.neverReachedPayer("Error")).isTrue();
        assertThat(PreAuthorizationCancelOutcome.neverReachedPayer("Processing Complete")).isFalse();
        assertThat(PreAuthorizationCancelOutcome.neverReachedPayer(null)).isFalse();
    }

    @Test
    void onlyACancelledOutcomeIsACancelByThePayer() {
        assertThat(PreAuthorizationCancelOutcome.cancelledByPayer(ACCEPTED, null)).isTrue();
        assertThat(PreAuthorizationCancelOutcome.cancelledByPayer(REFUSED, null)).isFalse();
    }

    @Test
    void aRefusedCancelExplainsWhy() {
        assertThat(PreAuthorizationCancelOutcome.reason(REFUSED, null))
                .isEqualTo("RE-00133: Task focus is not referring to a valid resource");
    }

    @Test
    void anUnpostedLineLeavesTheCancelledRequestAndWaitsForANewOne() {
        PatientServiceAndProduct line = line(1512L, false, PaymentStatus.SKIPPED_PENDING_PRE_AUTH);

        int released = releaseOf(line).release(request());

        assertThat(released).isEqualTo(1);
        assertThat(line.getPreAuthorizationRequestId()).isNull();
        assertThat(line.getPreAuthorizationStatus()).isEqualTo(PreAuthorizationStatus.PENDING_APPROVAL);
        assertThat(line.getPreAuthorizationRequired()).isTrue();
        assertThat(line.getPaymentStatus()).isEqualTo(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
    }

    @Test
    void aPostedLineKeepsItsPostingAndIsMarkedCancelled() {
        PatientServiceAndProduct line = line(1600L, true, PaymentStatus.PAID);

        releaseOf(line).release(request());

        assertThat(line.getPreAuthorizationStatus()).isEqualTo(PreAuthorizationStatus.CANCELLED);
        assertThat(line.getPreAuthorizationRequestId()).isEqualTo(38L);
        assertThat(line.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void aCancelledServiceIsLeftAsItIs() {
        PatientServiceAndProduct line = line(1601L, false, PaymentStatus.CANCELLED);

        assertThat(releaseOf(line).release(request())).isZero();
        assertThat(line.getPreAuthorizationRequestId()).isEqualTo(38L);
    }

    @Test
    void aRejectedLineKeepsThePayersDecision() {
        PatientServiceAndProduct line = line(1602L, false, PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
        line.setPreAuthorizationStatus(PreAuthorizationStatus.REJECTED);

        assertThat(releaseOf(line).release(request())).isZero();
        assertThat(line.getPreAuthorizationStatus()).isEqualTo(PreAuthorizationStatus.REJECTED);
    }

    /** Maha: teeth 13 and 34 approved, 43 partially approved and declined by the patient. */
    @Test
    void decliningOneToothKeepsTheRequestForTheApprovedTeeth() {
        PatientServiceAndProduct tooth13 = decided(1513L, PreAuthorizationStatus.APPROVED);
        PatientServiceAndProduct tooth43 = decided(1514L, PreAuthorizationStatus.PARTIAL);
        PatientServiceAndProduct tooth34 = decided(1515L, PreAuthorizationStatus.APPROVED);

        assertThat(PreAuthorizationCancellationService.otherLinesRelyOn(List.of(tooth13, tooth43, tooth34), tooth43)).isTrue();
    }

    @Test
    void theLastLineRelyingOnTheRequestCancelsIt() {
        PatientServiceAndProduct declined = decided(1513L, PreAuthorizationStatus.APPROVED);
        declined.setPaymentStatus(PaymentStatus.CANCELLED);
        PatientServiceAndProduct rejected = decided(1514L, PreAuthorizationStatus.REJECTED);
        PatientServiceAndProduct last = decided(1515L, PreAuthorizationStatus.PARTIAL);

        assertThat(PreAuthorizationCancellationService.otherLinesRelyOn(List.of(declined, rejected, last), last)).isFalse();
    }

    private static PatientServiceAndProduct decided(Long id, PreAuthorizationStatus status) {
        PatientServiceAndProduct line = line(id, false, PaymentStatus.PENDING);
        line.setPreAuthorizationStatus(status);
        return line;
    }

    private static PreAuthorizationLineReleaseService releaseOf(PatientServiceAndProduct line) {
        PatientServiceAndProductRepository repository = mock(PatientServiceAndProductRepository.class);
        when(repository.findByPreAuthorizationRequestId(38L)).thenReturn(List.of(line));
        return new PreAuthorizationLineReleaseService(repository);
    }

    private static PreAuthorizationRequest request() {
        return PreAuthorizationRequest.builder().id(38L).encounterId(529L).isCancelled(true).build();
    }

    private static PatientServiceAndProduct line(Long id, boolean billed, PaymentStatus paymentStatus) {
        PatientServiceAndProduct line = PatientServiceAndProduct.builder()
                .preAuthorizationRequestId(38L)
                .preAuthorizationStatus(PreAuthorizationStatus.PENDING_APPROVAL)
                .preAuthorizationRequired(true)
                .paymentStatus(paymentStatus)
                .isBilled(billed)
                .build();
        line.setId(id);
        return line;
    }
}
