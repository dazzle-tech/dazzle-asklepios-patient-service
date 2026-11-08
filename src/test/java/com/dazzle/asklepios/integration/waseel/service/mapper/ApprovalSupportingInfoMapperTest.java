package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.client.setup.DiagnosticTestClient;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.integration.waseel.service.pdf.InvestigationResultsPdfRenderer;
import com.dazzle.asklepios.repository.ApLovValueRepository;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.ChiefComplainRepository;
import com.dazzle.asklepios.repository.DepartmentRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.DiagnosticTestLaboratoryRepository;
import com.dazzle.asklepios.repository.DiagnosticTestRepository;
import com.dazzle.asklepios.repository.EncounterAssessmentRepository;
import com.dazzle.asklepios.repository.PatientObservationsComplaintsRepository;
import com.dazzle.asklepios.repository.ProgressNoteRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalSupportingInfoMapperTest {

    private static final Long ENCOUNTER_ID = 50224L;

    @Mock private ChiefComplainRepository chiefComplainRepository;
    @Mock private VitalSignsRepository vitalSignsRepository;
    @Mock private BodyMeasurementsRepository bodyMeasurementsRepository;
    @Mock private PatientObservationsComplaintsRepository patientObservationsComplaintsRepository;
    @Mock private ProgressNoteRepository progressNoteRepository;
    @Mock private EncounterAssessmentRepository encounterAssessmentRepository;
    @Mock private DiagnosticOrderRepository diagnosticOrderRepository;
    @Mock private DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    @Mock private DiagnosticOrderTestResultRepository diagnosticOrderTestResultRepository;
    @Mock private DiagnosticTestRepository diagnosticTestRepository;
    @Mock private DiagnosticTestLaboratoryRepository diagnosticTestLaboratoryRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private ApLovValueRepository apLovValueRepository;
    @Mock private DiagnosticTestClient diagnosticTestClient;
    @Spy private InvestigationResultsPdfRenderer investigationResultsPdfRenderer;

    @InjectMocks
    private ApprovalSupportingInfoMapper mapper;

    private final PatientEncounter encounter = PatientEncounter.builder()
            .id(ENCOUNTER_ID)
            .encounterDate(LocalDate.of(2026, 11, 5))
            .build();

    @BeforeEach
    void encounterWithALabResult() {
        Page<com.dazzle.asklepios.domain.ProgressNote> noNotes = new PageImpl<>(List.of());
        when(progressNoteRepository.findByEncounterIdAndCancelledDateIsNull(eq(ENCOUNTER_ID), any(Pageable.class)))
                .thenReturn(noNotes);
        when(diagnosticOrderRepository.findByEncounterIdAndStatusNot(eq(ENCOUNTER_ID), any()))
                .thenReturn(List.of(DiagnosticOrder.builder().id(1L).build()));
        when(diagnosticOrderTestRepository.findByOrderIdInAndStatusNot(anyCollection(), any()))
                .thenReturn(List.of(DiagnosticOrderTest.builder()
                        .id(2L)
                        .orderId(1L)
                        .testId(3L)
                        .orderType(TestType.LABORATORY)
                        .build()));
        when(diagnosticOrderTestResultRepository.findByOrderTestIdIn(anyList()))
                .thenReturn(List.of(DiagnosticOrderTestResult.builder()
                        .id(4L)
                        .orderTestId(2L)
                        .resultValueText("5")
                        .build()));
    }

    @Test
    void preAuthorizationAttachesInvestigationResultsAsPdf() {
        WaseelApprovalSupportingInfo investigation = investigationRow(mapper.toSupportingInfo(encounter));

        assertThat(investigation.code()).isEqualTo("IRA");
        assertThat(investigation.attachmentType()).isEqualTo("application/pdf");
        assertThat(investigation.attachmentName()).isEqualTo("investigation-results.pdf");
        assertThat(investigation.attachment()).isNotBlank();
        assertThat(investigation.attachmentDate()).isEqualTo("2026-11-05");
    }

    @Test
    void failedPdfNeverFallsBackToAPlainTextAttachment() {
        doThrow(new IllegalStateException("render failed"))
                .when(investigationResultsPdfRenderer).render(any());

        WaseelApprovalSupportingInfo investigation = investigationRow(mapper.toSupportingInfo(encounter));

        assertThat(investigation.code()).isEqualTo("IRP");
        assertThat(investigation.value()).isEqualTo("5");
        assertThat(investigation.attachment()).isNull();
        assertThat(investigation.attachmentType()).isNull();
    }

    private WaseelApprovalSupportingInfo investigationRow(List<WaseelApprovalSupportingInfo> rows) {
        return rows.stream()
                .filter(row -> "investigation-result".equals(row.category()))
                .findFirst()
                .orElseThrow();
    }
}
