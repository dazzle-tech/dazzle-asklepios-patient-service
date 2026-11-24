package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientPackage;
import com.dazzle.asklepios.domain.PatientPackageItem;
import com.dazzle.asklepios.domain.PatientPackageUsage;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PatientPackageStatus;
import com.dazzle.asklepios.domain.enumeration.Priority;
import com.dazzle.asklepios.domain.enumeration.ProcedureLevel;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientPackageItemRepository;
import com.dazzle.asklepios.repository.PatientPackageRepository;
import com.dazzle.asklepios.repository.PatientPackageUsageRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.DiagnosticOrderTestCreateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageDiagnosticsOrderVM;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageOrderVM;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ordering from the package panel: several tests in one diagnostic order (item lines and tests picked under a
 * category), each counted on the line it was picked under; a procedure counted on its line; the checks that
 * refuse a package of another visit, a cancelled package, a category line ordered alone, or the same test twice.
 */
class PatientPackageOrderScenarioTest {

    private static final long PATIENT = 10L;
    private static final long ENCOUNTER = 100L;
    private static final long CBC = 4L;
    private static final long GLUCOSE = 22L;

    @Mock private PatientPackageRepository packageRepository;
    @Mock private PatientPackageItemRepository itemRepository;
    @Mock private PatientPackageUsageRepository usageRepository;
    @Mock private PatientEncounterRepository encounterRepository;
    @Mock private DiagnosticOrderService diagnosticOrderService;
    @Mock private DiagnosticOrderTestService diagnosticOrderTestService;
    @Mock private PatientProcedureService patientProcedureService;
    @Mock private PatientServiceAndProductService patientServiceAndProductService;
    @Mock private EntityManager entityManager;
    @Mock private PatientPackageAutoApplyService autoApplyService;

    private PatientPackageOrderService service;
    private PatientPackage pkg;
    private PatientPackageItem cbcLine;
    private PatientPackageItem anyLab;
    private PatientPackageItem procedureLine;
    private final Map<Long, PatientPackageItem> items = new HashMap<>();
    private final AtomicLong nextTestId = new AtomicLong(500);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PatientPackageOrderService(packageRepository, itemRepository, usageRepository, encounterRepository,
                diagnosticOrderService, diagnosticOrderTestService, patientProcedureService, patientServiceAndProductService,
                entityManager, autoApplyService);

        Patient patient = new Patient();
        patient.setId(PATIENT);
        PatientEncounter encounter = new PatientEncounter();
        encounter.setId(ENCOUNTER);
        encounter.setPatient(patient);
        encounter.setDepartmentId(5000L);
        encounter.setFacilityId(1L);
        when(encounterRepository.findById(ENCOUNTER)).thenReturn(Optional.of(encounter));

        pkg = PatientPackage.builder().id(1L).patientId(PATIENT).encounterId(ENCOUNTER)
                .packageName("Lab 50% x5").status(PatientPackageStatus.ACTIVE).build();
        when(packageRepository.findById(1L)).thenAnswer(i -> Optional.of(pkg));

        cbcLine = item(11L, BillingItemTypes.LABORATORY, CBC, false, 1);
        anyLab = item(12L, BillingItemTypes.LABORATORY, null, true, 5);
        procedureLine = item(13L, BillingItemTypes.PROCEDURE, 2000L, false, 2);
        when(itemRepository.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(items.get((Long) i.getArgument(0))));
        when(itemRepository.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(items.get((Long) i.getArgument(0))));
        when(itemRepository.findByPatientPackageIdOrderByIdAsc(1L)).thenAnswer(i -> new ArrayList<>(items.values()));

        DiagnosticOrder order = new DiagnosticOrder();
        order.setId(900L);
        when(diagnosticOrderService.create(any())).thenReturn(order);
        when(diagnosticOrderTestService.create(any())).thenAnswer(i -> {
            DiagnosticOrderTest t = new DiagnosticOrderTest();
            t.setId(nextTestId.getAndIncrement());
            t.setTestId(((DiagnosticOrderTestCreateDTO) i.getArgument(0)).testId());
            return t;
        });
        when(autoApplyService.consumeLine(anyLong(), anyLong(), anyLong(), anyString(), anyLong()))
                .thenAnswer(i -> Optional.of(PatientPackageUsage.builder().id(1L).quantity(1).build()));
    }

    private PatientPackageItem item(long id, BillingItemTypes type, Long sourceId, boolean category, int qty) {
        PatientPackageItem it = PatientPackageItem.builder().id(id).patientPackageId(1L).itemType(type).sourceId(sourceId)
                .itemName(category ? null : "Item " + id).isCategory(category).quantityTotal(qty).quantityUsed(0).build();
        items.put(id, it);
        return it;
    }

    private static PatientPackageDiagnosticsOrderVM tests(PatientPackageDiagnosticsOrderVM.Line... lines) {
        return new PatientPackageDiagnosticsOrderVM(ENCOUNTER, false, null, null, null, List.of(lines));
    }

    private static PatientPackageDiagnosticsOrderVM.Line line(long itemId, Long testId) {
        return new PatientPackageDiagnosticsOrderVM.Line(itemId, testId, null);
    }

    // ---------- tests in one order ----------

    @Test
    void itemLineAndCategoryTestsGoInOneOrderAndCountOnTheirOwnLines() {
        var result = service.orderDiagnostics(tests(line(11L, null), line(12L, GLUCOSE)));

        verify(diagnosticOrderService, times(1)).create(any());
        ArgumentCaptor<DiagnosticOrderTestCreateDTO> created = ArgumentCaptor.forClass(DiagnosticOrderTestCreateDTO.class);
        verify(diagnosticOrderTestService, times(2)).create(created.capture());
        assertThat(created.getAllValues()).extracting(DiagnosticOrderTestCreateDTO::testId).containsExactly(CBC, GLUCOSE);
        verify(diagnosticOrderService, times(1)).submit(eq(900L), anyString());

        InOrder counting = inOrder(autoApplyService);
        counting.verify(autoApplyService).consumeLine(eq(11L), eq(ENCOUNTER), eq(CBC), eq("LABORATORY"), eq(500L));
        counting.verify(autoApplyService).consumeLine(eq(12L), eq(ENCOUNTER), eq(GLUCOSE), eq("LABORATORY"), eq(501L));
        assertThat(result.countedTestIds()).containsExactly(500L, 501L);
    }

    @Test
    void testsWithoutRoomAreStillOrderedButNotCounted() {
        when(autoApplyService.consumeLine(eq(12L), anyLong(), eq(CBC), anyString(), anyLong())).thenReturn(Optional.empty());

        var result = service.orderDiagnostics(tests(line(12L, GLUCOSE), line(12L, CBC)));

        assertThat(result.diagnosticOrderTestIds()).hasSize(2);
        assertThat(result.countedTestIds()).containsExactly(500L);
    }

    @Test
    void categoryLineNeedsTheTestPicked() {
        assertThatThrownBy(() -> service.orderDiagnostics(tests(line(12L, null))))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("Choose the test");
        verify(diagnosticOrderService, never()).create(any());
    }

    @Test
    void theSameTestTwiceIsRefused() {
        assertThatThrownBy(() -> service.orderDiagnostics(tests(line(11L, null), line(12L, CBC))))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("same test");
        verify(diagnosticOrderService, never()).create(any());
    }

    @Test
    void packageOfAnotherVisitIsRefused() {
        pkg.setEncounterId(999L);

        assertThatThrownBy(() -> service.orderDiagnostics(tests(line(11L, null))))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("another visit");
    }

    @Test
    void cancelledPackageIsRefused() {
        pkg.setStatus(PatientPackageStatus.CANCELLED);

        assertThatThrownBy(() -> service.orderDiagnostics(tests(line(11L, null))))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("cancelled");
    }

    // ---------- one item ----------

    private static PatientPackageOrderVM procedureOrder() {
        return new PatientPackageOrderVM(ENCOUNTER, 1, false, null, null, null, null, null,
                ProcedureLevel.values()[0], Priority.values()[0], "Arm", null, Instant.now(), null, null);
    }

    @Test
    void procedureIsCountedOnItsLine() {
        PatientProcedure procedure = new PatientProcedure();
        procedure.setId(77L);
        when(patientProcedureService.create(any())).thenReturn(procedure);

        service.order(13L, procedureOrder());

        verify(patientProcedureService).create(any());
        verify(autoApplyService).consumeLine(eq(13L), eq(ENCOUNTER), eq(2000L), eq("PROCEDURE"), eq(77L));
    }

    @Test
    void procedureWithoutDetailsIsRefused() {
        PatientPackageOrderVM noDetails = new PatientPackageOrderVM(ENCOUNTER, 1, false, null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.order(13L, noDetails))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("required");
        verify(autoApplyService, never()).consumeLine(anyLong(), anyLong(), anyLong(), anyString(), anyLong());
    }

    @Test
    void categoryLineCannotBeOrderedAlone() {
        assertThatThrownBy(() -> service.order(12L, procedureOrder()))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("Choose the item");
    }

    @Test
    void lineWithNothingLeftIsRefused() {
        procedureLine.setQuantityUsed(2);

        assertThatThrownBy(() -> service.order(13L, procedureOrder()))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("Only 0 left");
        verify(patientProcedureService, never()).create(any());
    }
}
