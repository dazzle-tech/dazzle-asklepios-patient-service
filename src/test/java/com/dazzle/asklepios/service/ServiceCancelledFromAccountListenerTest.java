package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DentalProcedure;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.DentalToothApprovalStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.repository.DentalProcedureRepository;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.event.ServiceCancelledFromAccountEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceCancelledFromAccountListenerTest {

    private static final String REASON = "Cancelled from patient account";

    private PatientServiceAndProductRepository lines;
    private DentalProcedureRepository dentalProcedures;
    private PatientProcedureRepository procedures;
    private DentalProcedureService dentalProcedureService;
    private PatientProcedureService patientProcedureService;
    private ServiceCancelledFromAccountListener listener;

    @BeforeEach
    void setUp() {
        lines = mock(PatientServiceAndProductRepository.class);
        dentalProcedures = mock(DentalProcedureRepository.class);
        procedures = mock(PatientProcedureRepository.class);
        dentalProcedureService = mock(DentalProcedureService.class);
        patientProcedureService = mock(PatientProcedureService.class);
        listener = new ServiceCancelledFromAccountListener(
                lines, dentalProcedures, procedures, dentalProcedureService, patientProcedureService);
        when(dentalProcedures.findById(17L)).thenReturn(Optional.of(DentalProcedure.builder().id(17L).build()));
    }

    @Test
    void aDentalProcedureStaysWhileAToothIsStillInUse() {
        toothLines(line(PaymentStatus.CANCELLED), line(PaymentStatus.PENDING));

        listener.onServiceCancelled(dentalEvent());

        verify(dentalProcedureService, never()).cancel(anyLong(), any());
    }

    @Test
    void aDentalProcedureIsCancelledWithItsLastTooth() {
        toothLines(line(PaymentStatus.CANCELLED), line(PaymentStatus.CANCELLED));

        listener.onServiceCancelled(dentalEvent());

        verify(dentalProcedureService).cancel(17L, REASON);
    }

    @Test
    void aPerformedProcedureIsNotCancelled() {
        when(procedures.findById(5L)).thenReturn(Optional.of(procedure(ProcStatus.COMPLETED)));

        listener.onServiceCancelled(new ServiceCancelledFromAccountEvent(
                ServiceSource.PROCEDURE, 5L, BillingItemTypes.PROCEDURE, REASON));

        verify(patientProcedureService, never()).cancel(anyLong(), any());
    }

    @Test
    void aProcedureWaitingForApprovalIsCancelledWithItsLine() {
        when(procedures.findById(5L)).thenReturn(Optional.of(procedure(ProcStatus.WAITING_PRE_AUTHORIZATION)));

        listener.onServiceCancelled(new ServiceCancelledFromAccountEvent(
                ServiceSource.PROCEDURE, 5L, BillingItemTypes.PROCEDURE, REASON));

        verify(patientProcedureService).cancel(5L, REASON);
    }

    @Test
    void eachToothShowsItsInsuranceState() {
        PatientServiceAndProduct partial = line(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
        partial.setPreAuthorizationStatus(PreAuthorizationStatus.PARTIAL);
        PatientServiceAndProduct declined = line(PaymentStatus.CANCELLED);
        declined.setPreAuthorizationStatus(PreAuthorizationStatus.PARTIAL);
        PatientServiceAndProduct waiting = line(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
        waiting.setPreAuthorizationStatus(PreAuthorizationStatus.CANCELLED);
        waiting.setPreAuthorizationRequired(true);

        assertThat(DentalToothApprovalStatus.of(partial)).isEqualTo(DentalToothApprovalStatus.PARTIALLY_APPROVED);
        assertThat(DentalToothApprovalStatus.of(declined)).isEqualTo(DentalToothApprovalStatus.CANCELLED);
        assertThat(DentalToothApprovalStatus.of(waiting)).isEqualTo(DentalToothApprovalStatus.PENDING_APPROVAL);
        assertThat(DentalToothApprovalStatus.of(line(PaymentStatus.PAID))).isEqualTo(DentalToothApprovalStatus.NO_APPROVAL_NEEDED);
    }

    private void toothLines(PatientServiceAndProduct... toothLines) {
        when(lines.findAllByServiceSourceAndSourceIdAndBillingItemTypeOrderByIdAsc(
                ServiceSource.DENTAL_PROCEDURE, 17L, BillingItemTypes.PROCEDURE)).thenReturn(List.of(toothLines));
    }

    private static ServiceCancelledFromAccountEvent dentalEvent() {
        return new ServiceCancelledFromAccountEvent(ServiceSource.DENTAL_PROCEDURE, 17L, BillingItemTypes.PROCEDURE, REASON);
    }

    private static PatientServiceAndProduct line(PaymentStatus paymentStatus) {
        return PatientServiceAndProduct.builder().paymentStatus(paymentStatus).build();
    }

    private static PatientProcedure procedure(ProcStatus status) {
        PatientProcedure procedure = new PatientProcedure();
        procedure.setId(5L);
        procedure.setStatus(status);
        return procedure;
    }
}
