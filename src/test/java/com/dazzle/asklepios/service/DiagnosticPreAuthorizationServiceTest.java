package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiagnosticPreAuthorizationServiceTest {

    private static final Long ENCOUNTER_ID = 7L;
    private static final Long ORDER_TEST_ID = 30L;
    private static final Long LINE_ID = 90L;

    private final PatientServiceAndProductRepository lineRepository = mock(PatientServiceAndProductRepository.class);
    private final BillingChargeLineRepository chargeLineRepository = mock(BillingChargeLineRepository.class);
    private final CoverageApprovalRoutingService routingService = mock(CoverageApprovalRoutingService.class);

    private final DiagnosticPreAuthorizationService service = new DiagnosticPreAuthorizationService(
            lineRepository,
            chargeLineRepository,
            routingService,
            mock(DiagnosticOrderTestRepository.class),
            mock(DiagnosticOrderRepository.class),
            mock(DiagnosticOrderStatusService.class)
    );

    @BeforeEach
    void waseelVisit() {
        when(routingService.encounterUsesLocalApproval(ENCOUNTER_ID)).thenReturn(false);
    }

    @Test
    void waseelRejectedTestWaitsOnThePatientChoice() {
        givenLine(PreAuthorizationStatus.REJECTED);

        assertThatThrownBy(() -> service.requireCollectable(ORDER_TEST_ID))
                .isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void waseelRejectedTestPaidAsCashCanBeCollected() {
        givenLine(PreAuthorizationStatus.REJECTED);
        BillingChargeLine chargeLine = mock(BillingChargeLine.class);
        when(chargeLine.getPatientServiceProduct())
                .thenReturn(PatientServiceAndProduct.builder().id(LINE_ID).build());
        when(chargeLineRepository.findAllByEncounter_IdAndStatusNotInOrderByIdAsc(eq(ENCOUNTER_ID), any()))
                .thenReturn(List.of(chargeLine));

        assertThatCode(() -> service.requireCollectable(ORDER_TEST_ID)).doesNotThrowAnyException();
    }

    @Test
    void waseelPendingTestKeepsItsCurrentBehaviour() {
        givenLine(PreAuthorizationStatus.PENDING_APPROVAL);

        assertThatCode(() -> service.requireCollectable(ORDER_TEST_ID)).doesNotThrowAnyException();
    }

    private void givenLine(PreAuthorizationStatus status) {
        PatientServiceAndProduct line = PatientServiceAndProduct.builder()
                .id(LINE_ID)
                .encounterId(ENCOUNTER_ID)
                .serviceSource(ServiceSource.LABORATORY)
                .sourceId(ORDER_TEST_ID)
                .preAuthorizationStatus(status)
                .build();
        when(lineRepository.findByServiceSourceInAndSourceIdIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(line));
    }
}
