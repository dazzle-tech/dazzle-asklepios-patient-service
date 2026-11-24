package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientPackage;
import com.dazzle.asklepios.domain.PatientPackageItem;
import com.dazzle.asklepios.domain.PatientPackageUsage;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PatientPackageStatus;
import com.dazzle.asklepios.domain.enumeration.PatientPackageUsageStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientPackageItemRepository;
import com.dazzle.asklepios.repository.PatientPackageRepository;
import com.dazzle.asklepios.repository.PatientPackageUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * How orders made on the usual screens reach the package: a submitted lab order (tests counted in the order
 * they were added, microbiology as laboratory, never twice), a procedure order, and a service line whose
 * quantity is edited afterwards. Repositories are in-memory; the reversal service is the real one.
 */
class PatientPackageHooksScenarioTest {

    private static final long ENCOUNTER = 100L;

    @Mock private PatientPackageRepository packageRepository;
    @Mock private PatientPackageItemRepository itemRepository;
    @Mock private PatientPackageUsageRepository usageRepository;
    @Mock private DiagnosticOrderRepository diagnosticOrderRepository;
    @Mock private DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    private PatientPackageAutoApplyService service;
    private final Map<Long, PatientPackage> packages = new LinkedHashMap<>();
    private final Map<Long, PatientPackageItem> items = new LinkedHashMap<>();
    private final List<PatientPackageUsage> usages = new ArrayList<>();
    private final List<DiagnosticOrderTest> orderTests = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PatientPackageAutoApplyService(
                packageRepository, itemRepository, usageRepository, diagnosticOrderRepository, diagnosticOrderTestRepository);
        service.setReversalService(new PatientPackageUsageReversalService(usageRepository, itemRepository, packageRepository));

        when(packageRepository.findByEncounterIdOrderByIdAsc(anyLong())).thenAnswer(i -> packages.values().stream()
                .filter(p -> p.getEncounterId().equals(i.getArgument(0))).toList());
        when(packageRepository.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(packages.get((Long) i.getArgument(0))));
        when(packageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(itemRepository.findByPatientPackageIdOrderByIdAsc(anyLong())).thenAnswer(i -> items.values().stream()
                .filter(it -> it.getPatientPackageId().equals(i.getArgument(0))).toList());
        when(itemRepository.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(items.get((Long) i.getArgument(0))));
        when(itemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(itemRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(usageRepository.save(any())).thenAnswer(i -> {
            PatientPackageUsage u = i.getArgument(0);
            if (u.getId() == null) {
                u.setId((long) usages.size() + 1);
                usages.add(u);
            }
            return u;
        });
        when(usageRepository.sumQuantity(anyLong(), anyLong(), any())).thenAnswer(i ->
                usages.stream().filter(u -> u.getPatientPackageItemId().equals(i.getArgument(0))
                        && u.getSourceId().equals(i.getArgument(1)) && u.getStatus() == i.getArgument(2))
                        .mapToLong(PatientPackageUsage::getQuantity).sum());
        when(usageRepository.findByOrderSourceInAndOrderIdAndStatus(anyCollection(), anyLong(), any())).thenAnswer(i -> {
            Collection<String> sources = i.getArgument(0);
            Long orderId = i.getArgument(1);
            PatientPackageUsageStatus status = i.getArgument(2);
            return usages.stream().filter(u -> sources.contains(u.getOrderSource())
                    && orderId.equals(u.getOrderId()) && u.getStatus() == status).toList();
        });

        DiagnosticOrder order = new DiagnosticOrder();
        order.setId(900L);
        order.setEncounterId(ENCOUNTER);
        when(diagnosticOrderRepository.findById(900L)).thenReturn(Optional.of(order));
        when(diagnosticOrderTestRepository.findByOrderIdInAndStatusNot(any(), any())).thenAnswer(i -> new ArrayList<>(orderTests));

        packages.put(1L, PatientPackage.builder().id(1L).patientId(10L).encounterId(ENCOUNTER)
                .packageName("PKG").status(PatientPackageStatus.ACTIVE).build());
    }

    private PatientPackageItem item(long id, BillingItemTypes type, Long sourceId, boolean category, int qty) {
        PatientPackageItem it = PatientPackageItem.builder().id(id).patientPackageId(1L).itemType(type).sourceId(sourceId)
                .isCategory(category).quantityTotal(qty).quantityUsed(0).build();
        items.put(id, it);
        return it;
    }

    private void testInOrder(long id, long testId, TestType type) {
        DiagnosticOrderTest t = new DiagnosticOrderTest();
        t.setId(id);
        t.setTestId(testId);
        t.setOrderType(type);
        orderTests.add(t);
    }

    // ---------- lab order submitted on the usual screen ----------

    @Test
    void submittedTestsAreCountedInTheOrderTheyWereAdded() {
        PatientPackageItem anyLab = item(12L, BillingItemTypes.LABORATORY, null, true, 2);
        testInOrder(502L, 24L, TestType.LABORATORY);  // added last
        testInOrder(500L, 4L, TestType.MICROBIOLOGY); // added first; microbiology counts as laboratory
        testInOrder(501L, 22L, TestType.LABORATORY);

        service.onDiagnosticOrderSubmitted(900L);

        assertThat(anyLab.getQuantityUsed()).isEqualTo(2);
        assertThat(usages).extracting(PatientPackageUsage::getOrderId).containsExactly(500L, 501L);
    }

    @Test
    void submittingAgainDoesNotCountTwice() {
        PatientPackageItem anyLab = item(12L, BillingItemTypes.LABORATORY, null, true, 5);
        testInOrder(500L, 4L, TestType.LABORATORY);

        service.onDiagnosticOrderSubmitted(900L);
        service.onDiagnosticOrderSubmitted(900L);

        assertThat(anyLab.getQuantityUsed()).isEqualTo(1);
    }

    @Test
    void radiologyTestIsNotTakenFromALaboratoryLine() {
        PatientPackageItem anyLab = item(12L, BillingItemTypes.LABORATORY, null, true, 5);
        testInOrder(500L, 1L, TestType.RADIOLOGY);

        service.onDiagnosticOrderSubmitted(900L);

        assertThat(anyLab.getQuantityUsed()).isZero();
    }

    @Test
    void ordersMadeByThePanelAreLeftToThePanel() {
        PatientPackageItem anyLab = item(12L, BillingItemTypes.LABORATORY, null, true, 5);
        testInOrder(500L, 4L, TestType.LABORATORY);

        PatientPackageAutoApplyService.withoutAutoApply(() -> {
            service.onDiagnosticOrderSubmitted(900L);
            return null;
        });

        assertThat(anyLab.getQuantityUsed()).isZero();
    }

    // ---------- procedure ordered on the usual screen ----------

    @Test
    void procedureOrderIsCountedOnItsLine() {
        PatientPackageItem fees = item(13L, BillingItemTypes.PROCEDURE, 2000L, false, 1);
        PatientEncounter encounter = new PatientEncounter();
        encounter.setId(ENCOUNTER);
        PatientProcedure procedure = new PatientProcedure();
        procedure.setId(77L);
        procedure.setProcedureId(2000L);
        procedure.setEncounter(encounter);

        service.onProcedureCreated(procedure);

        assertThat(fees.getQuantityUsed()).isEqualTo(1);
        assertThat(usages.get(0).getOrderSource()).isEqualTo("PROCEDURE");
        assertThat(usages.get(0).getOrderId()).isEqualTo(77L);
    }

    // ---------- a counted service line edited afterwards ----------

    private PatientServiceAndProduct serviceLine(ServiceSource source, long quantity) {
        PatientServiceAndProduct l = new PatientServiceAndProduct();
        l.setId(300L);
        l.setEncounterId(ENCOUNTER);
        l.setServiceSource(source);
        l.setBillingItemType(BillingItemTypes.SERVICE);
        l.setServiceId(1501L);
        l.setQuantity(quantity);
        return l;
    }

    @Test
    void raisingTheQuantityTakesMoreFromThePackage() {
        PatientPackageItem dental = item(31L, BillingItemTypes.SERVICE, 1501L, false, 5);
        PatientServiceAndProduct line = serviceLine(ServiceSource.SERVICE_AND_PRODUCT, 1);
        service.onServiceLineCreated(line);

        line.setQuantity(3L);
        service.onServiceLineUpdated(line);

        assertThat(dental.getQuantityUsed()).isEqualTo(3);
        assertThat(usages).extracting(PatientPackageUsage::getStatus)
                .containsExactly(PatientPackageUsageStatus.REVERSED, PatientPackageUsageStatus.USED);
    }

    @Test
    void loweringTheQuantityGivesUnitsBack() {
        PatientPackageItem dental = item(31L, BillingItemTypes.SERVICE, 1501L, false, 5);
        PatientServiceAndProduct line = serviceLine(ServiceSource.SERVICE_AND_PRODUCT, 4);
        service.onServiceLineCreated(line);

        line.setQuantity(1L);
        service.onServiceLineUpdated(line);

        assertThat(dental.getQuantityUsed()).isEqualTo(1);
    }

    @Test
    void panelServiceLineIsNotRecountedWhenEdited() {
        PatientPackageItem dental = item(31L, BillingItemTypes.SERVICE, 1501L, false, 5);
        dental.setQuantityUsed(1);
        usages.add(PatientPackageUsage.builder().id(1L).patientPackageId(1L).patientPackageItemId(31L)
                .sourceId(1501L).quantity(1).orderSource("PACKAGE").orderId(300L)
                .status(PatientPackageUsageStatus.USED).build());

        service.onServiceLineUpdated(serviceLine(ServiceSource.PACKAGE, 3));

        assertThat(dental.getQuantityUsed()).isEqualTo(1);
        assertThat(usages).hasSize(1);
    }
}
