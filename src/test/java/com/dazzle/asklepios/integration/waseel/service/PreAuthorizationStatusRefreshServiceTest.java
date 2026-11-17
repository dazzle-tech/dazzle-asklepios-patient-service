package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.dto.preAuthorization.response.PreAuthorizationSearchResponse;
import com.dazzle.asklepios.integration.waseel.event.PreAuthorizationApprovedEvent;
import com.dazzle.asklepios.integration.waseel.event.PreAuthorizationDecisionCommittedEvent;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PreAuthorizationItemRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.service.CoverageApprovalRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreAuthorizationStatusRefreshServiceTest {

    private static final Long ENCOUNTER_ID = 616L;

    @Mock private EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;
    @Mock private WaseelPreAuthorizationService waseelPreAuthorizationService;
    @Mock private PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    @Mock private PreAuthorizationItemRepository preAuthorizationItemRepository;
    @Mock private PatientServiceAndProductRepository patientServiceAndProductRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private CoverageApprovalRoutingService coverageApprovalRoutingService;
    @Mock private PreAuthorizationLineReleaseService preAuthorizationLineReleaseService;

    @InjectMocks
    private PreAuthorizationStatusRefreshService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(encounterInsuranceEligibilityService.isInsuranceEncounter(ENCOUNTER_ID)).thenReturn(true);
        when(coverageApprovalRoutingService.encounterUsesLocalApproval(ENCOUNTER_ID)).thenReturn(true);
    }

    @Test
    void decidedLines_refreshResponsibilitiesAfterCommitNotInsideTheDecisionTransaction() {
        PreAuthorizationRequest request = PreAuthorizationRequest.builder()
                .id(4L)
                .encounterId(ENCOUNTER_ID)
                .status("Approved")
                .outcome("Approved")
                .build();
        PatientServiceAndProduct line = PatientServiceAndProduct.builder()
                .id(90L)
                .encounterId(ENCOUNTER_ID)
                .preAuthorizationRequestId(4L)
                .preAuthorizationStatus(PreAuthorizationStatus.PENDING_APPROVAL)
                .build();
        when(preAuthorizationRequestRepository.findByEncounterIdOrderByIdDesc(ENCOUNTER_ID))
                .thenReturn(List.of(request));
        when(patientServiceAndProductRepository.findByPreAuthorizationRequestId(any()))
                .thenReturn(List.of(line));
        when(preAuthorizationItemRepository.findByPreAuthorizationIdOrderBySequenceAsc(4L))
                .thenReturn(List.of());

        service.refreshEncounter(ENCOUNTER_ID);

        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(applicationEventPublisher, atLeastOnce()).publishEvent(events.capture());
        List<Object> published = events.getAllValues();
        assertThat(published).hasSize(2);
        assertThat(published.get(0)).isInstanceOf(PreAuthorizationApprovedEvent.class);
        assertThat(published.get(1)).isEqualTo(new PreAuthorizationDecisionCommittedEvent(ENCOUNTER_ID));
    }

    @Test
    void screenRefreshBringsARejectionToTheBillingLine() {
        PatientServiceAndProduct line = heldLine();
        givenSearchResult("rejected", "Processing Complete", List.of(line));

        service.refreshRequest(5L, 500L);

        assertThat(line.getPreAuthorizationStatus()).isEqualTo(PreAuthorizationStatus.REJECTED);
        assertThat(line.getPreAuthorizationRequired()).isFalse();
        assertThat(line.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(applicationEventPublisher).publishEvent(new PreAuthorizationDecisionCommittedEvent(ENCOUNTER_ID));
    }

    @Test
    void screenRefreshKeepsTheLineHeldOnATechnicalError() {
        PatientServiceAndProduct line = heldLine();
        givenSearchResult(null, "error", List.of(line));

        service.refreshRequest(5L, 500L);

        assertThat(line.getPreAuthorizationStatus()).isEqualTo(PreAuthorizationStatus.PENDING_APPROVAL);
        assertThat(line.getPreAuthorizationRequired()).isTrue();
        assertThat(line.getPaymentStatus()).isEqualTo(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
    }

    @Test
    void screenRefreshOfAReplacedRequestLeavesBillingLinesAlone() {
        givenSearchResult("rejected", "Processing Complete", List.of());

        service.refreshRequest(5L, 500L);

        verify(preAuthorizationItemRepository, never()).findByPreAuthorizationIdOrderBySequenceAsc(any());
        verify(applicationEventPublisher, never()).publishEvent(any(Object.class));
    }

    private PatientServiceAndProduct heldLine() {
        return PatientServiceAndProduct.builder()
                .id(91L)
                .encounterId(ENCOUNTER_ID)
                .preAuthorizationRequestId(5L)
                .preAuthorizationRequired(true)
                .paymentStatus(PaymentStatus.SKIPPED_PENDING_PRE_AUTH)
                .preAuthorizationStatus(PreAuthorizationStatus.PENDING_APPROVAL)
                .build();
    }

    private void givenSearchResult(String status, String outcome, List<PatientServiceAndProduct> lines) {
        PreAuthorizationRequest request = PreAuthorizationRequest.builder()
                .id(5L)
                .encounterId(ENCOUNTER_ID)
                .approvalRequestId(500L)
                .build();
        PreAuthorizationSearchResponse response = mock(PreAuthorizationSearchResponse.class);
        when(response.status()).thenReturn(status);
        when(response.outcome()).thenReturn(outcome);
        when(waseelPreAuthorizationService.searchAndUpdate(5L, 500L)).thenReturn(response);
        when(preAuthorizationRequestRepository.findById(5L)).thenReturn(Optional.of(request));
        when(patientServiceAndProductRepository.findByPreAuthorizationRequestId(5L)).thenReturn(lines);
        when(preAuthorizationItemRepository.findByPreAuthorizationIdOrderBySequenceAsc(5L)).thenReturn(List.of());
    }
}
