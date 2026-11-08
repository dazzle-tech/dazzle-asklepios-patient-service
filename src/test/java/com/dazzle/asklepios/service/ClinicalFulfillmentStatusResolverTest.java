package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.repository.ConsultationRepository;
import com.dazzle.asklepios.repository.DentalProcedureRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ClinicalFulfillmentStatusResolverTest {

    @Mock
    private DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    @Mock
    private PatientProcedureRepository patientProcedureRepository;

    @Mock
    private PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository;

    @Mock
    private ConsultationRepository consultationRepository;

    @Mock
    private DentalProcedureRepository dentalProcedureRepository;

    @Mock
    private PatientServiceAndProductRepository patientServiceAndProductRepository;

    @InjectMocks
    private ClinicalFulfillmentStatusResolver resolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(diagnosticOrderTestRepository.findActiveByEncounterIdAndTestIdIn(any(), any()))
                .thenReturn(List.of());
        when(diagnosticOrderTestRepository.findActiveByEncounterId(any()))
                .thenReturn(List.of());
    }

    @Test
    void attachToItems_setsLabProcessingStatus() {
        PatientServiceAndProduct labItem = PatientServiceAndProduct.builder()
                .id(1L)
                .billingItemType(BillingItemTypes.LABORATORY)
                .serviceSource(ServiceSource.LABORATORY)
                .sourceId(44L)
                .build();

        when(diagnosticOrderTestRepository.findAllById(any()))
                .thenReturn(List.of(
                        DiagnosticOrderTest.builder()
                                .id(44L)
                                .processingStatus(DiagnosticStatus.RESULT_READY)
                                .build()
                ));

        resolver.attachToItems(List.of(labItem));

        assertThat(labItem.getClinicalStatus()).isEqualTo("RESULT_READY");
    }

    @Test
    void attachToItems_usesProcessingStatusNotOrderStatus() {
        PatientServiceAndProduct labItem = PatientServiceAndProduct.builder()
                .id(1L)
                .encounterId(8L)
                .billingItemType(BillingItemTypes.LABORATORY)
                .serviceSource(ServiceSource.LABORATORY)
                .sourceId(44L)
                .diagnosticTestId(100L)
                .build();

        when(diagnosticOrderTestRepository.findAllById(any()))
                .thenReturn(List.of(
                        DiagnosticOrderTest.builder()
                                .id(44L)
                                .testId(100L)
                                .processingStatus(DiagnosticStatus.SAMPLE_COLLECTED)
                                .status(com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus.SUBMITTED)
                                .build()
                ));

        resolver.attachToItems(List.of(labItem));

        assertThat(labItem.getClinicalStatus()).isEqualTo("SAMPLE_COLLECTED");
    }

    @Test
    void attachToItems_setsProcedureStatus() {
        PatientServiceAndProduct procedureItem = PatientServiceAndProduct.builder()
                .id(2L)
                .billingItemType(BillingItemTypes.PROCEDURE)
                .serviceSource(ServiceSource.PROCEDURE)
                .sourceId(9L)
                .build();

        PatientProcedure procedure = new PatientProcedure();
        procedure.setId(9L);
        procedure.setStatus(ProcStatus.COMPLETED);

        when(patientProcedureRepository.findAllById(any()))
                .thenReturn(List.of(procedure));

        resolver.attachToItems(List.of(procedureItem));

    @Test
    void attachToItems_setsRadiologyProcessingStatus() {
        PatientServiceAndProduct radiologyItem = PatientServiceAndProduct.builder()
                .id(4L)
                .encounterId(8L)
                .billingItemType(BillingItemTypes.RADIOLOGY)
                .serviceSource(ServiceSource.RADIOLOGY)
                .sourceId(55L)
                .diagnosticTestId(200L)
                .build();

        when(diagnosticOrderTestRepository.findAllById(any()))
                .thenReturn(List.of(
                        DiagnosticOrderTest.builder()
                                .id(55L)
                                .testId(200L)
                                .processingStatus(DiagnosticStatus.EXAM_DONE)
                                .build()
                ));

        resolver.attachToItems(List.of(radiologyItem));

        assertThat(radiologyItem.getClinicalStatus()).isEqualTo("EXAM_DONE");
    }

    @Test
    void attachToItems_setsPathologyProcessingStatus() {
        PatientServiceAndProduct pathologyItem = PatientServiceAndProduct.builder()
                .id(5L)
                .encounterId(8L)
                .billingItemType(BillingItemTypes.PATHOLOGY)
                .serviceSource(ServiceSource.SERVICE_AND_PRODUCT)
                .sourceId(66L)
                .diagnosticTestId(300L)
                .build();

        when(diagnosticOrderTestRepository.findAllById(any()))
                .thenReturn(List.of(
                        DiagnosticOrderTest.builder()
                                .id(66L)
                                .testId(300L)
                                .processingStatus(DiagnosticStatus.RESULT_APPROVED)
                                .build()
                ));

        resolver.attachToItems(List.of(pathologyItem));

        assertThat(pathologyItem.getClinicalStatus()).isEqualTo("RESULT_APPROVED");
    }

    @Test
    void attachToItems_leavesCatalogServiceWithoutStatus() {
        PatientServiceAndProduct serviceItem = PatientServiceAndProduct.builder()
                .id(3L)
                .billingItemType(BillingItemTypes.SERVICE)
                .serviceSource(ServiceSource.SERVICE_AND_PRODUCT)
                .sourceId(12L)
                .build();

        resolver.attachToItems(List.of(serviceItem));

        assertThat(serviceItem.getClinicalStatus()).isNull();
    }
}
