package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientPackage;
import com.dazzle.asklepios.domain.PatientPackageItem;
import com.dazzle.asklepios.domain.PatientPackageUsage;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PatientPackageStatus;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Counting orders against the visit's packages: own item line before category, repeat limit,
 * orders beyond the room left, the line chosen in the panel, and validity after the package was given.
 * Repositories are backed by in-memory lists.
 */
class PatientPackageMatchingScenarioTest {

    private static final long ENCOUNTER = 100L;
    private static final long CBC = 4L;
    private static final long GLUCOSE = 22L;
    private static final long HBA1C = 24L;
    private static final long XRAY = 1L;

    @Mock private PatientPackageRepository packageRepository;
    @Mock private PatientPackageItemRepository itemRepository;
    @Mock private PatientPackageUsageRepository usageRepository;
    @Mock private DiagnosticOrderRepository diagnosticOrderRepository;
    @Mock private DiagnosticOrderTestRepository diagnosticOrderTestRepository;

    private PatientPackageAutoApplyService service;
    private final Map<Long, PatientPackage> packages = new LinkedHashMap<>();
    private final Map<Long, PatientPackageItem> items = new LinkedHashMap<>();
    private final List<PatientPackageUsage> usages = new ArrayList<>();
    private long nextOrderId = 1000L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PatientPackageAutoApplyService(
                packageRepository, itemRepository, usageRepository, diagnosticOrderRepository, diagnosticOrderTestRepository);

        when(packageRepository.findByEncounterIdOrderByIdAsc(anyLong())).thenAnswer(i -> packages.values().stream()
                .filter(p -> p.getEncounterId().equals(i.getArgument(0))).toList());
        when(packageRepository.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(packages.get((Long) i.getArgument(0))));
        when(itemRepository.findByPatientPackageIdOrderByIdAsc(anyLong())).thenAnswer(i -> items.values().stream()
                .filter(it -> it.getPatientPackageId().equals(i.getArgument(0))).toList());
        when(itemRepository.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(items.get((Long) i.getArgument(0))));
        when(itemRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(usageRepository.save(any())).thenAnswer(i -> {
            PatientPackageUsage u = i.getArgument(0);
            u.setId((long) usages.size() + 1);
            usages.add(u);
            return u;
        });
        when(usageRepository.countByPatientPackageItemIdAndSourceIdAndStatus(anyLong(), anyLong(), any())).thenAnswer(i ->
                usages.stream().filter(u -> u.getPatientPackageItemId().equals(i.getArgument(0))
                        && u.getSourceId().equals(i.getArgument(1)) && u.getStatus() == i.getArgument(2)).count());
    }

    // ---------- usual screens: automatic matching ----------

    @Test
    void ownItemLineIsUsedBeforeTheCategory() {
        addPackage(1L);
        PatientPackageItem cbcLine = addItem(1L, 11L, BillingItemTypes.LABORATORY, CBC, 1, false, null);
        PatientPackageItem anyLab = addItem(1L, 12L, BillingItemTypes.LABORATORY, null, 5, true, null);

        Optional<PatientPackageUsage> usage = orderLab(CBC);

        assertThat(usage).isPresent();
        assertThat(usage.get().getPatientPackageItemId()).isEqualTo(cbcLine.getId());
        assertThat(cbcLine.getQuantityUsed()).isEqualTo(1);
        assertThat(anyLab.getQuantityUsed()).isZero();
    }

    @Test
    void whenTheOwnLineIsUsedUpTheCategoryTakesIt() {
        addPackage(1L);
        PatientPackageItem cbcLine = addItem(1L, 11L, BillingItemTypes.LABORATORY, CBC, 1, false, null);
        PatientPackageItem anyLab = addItem(1L, 12L, BillingItemTypes.LABORATORY, null, 5, true, null);

        orderLab(CBC);
        Optional<PatientPackageUsage> second = orderLab(CBC);

        assertThat(second).isPresent();
        assertThat(second.get().getPatientPackageItemId()).isEqualTo(anyLab.getId());
        assertThat(cbcLine.getQuantityRemaining()).isZero();
        assertThat(anyLab.getQuantityUsed()).isEqualTo(1);
    }

    @Test
    void categoryCountsAnyTestOfItsTypeButNotOtherTypes() {
        addPackage(1L);
        PatientPackageItem anyLab = addItem(1L, 12L, BillingItemTypes.LABORATORY, null, 5, true, null);

        assertThat(orderLab(GLUCOSE)).isPresent();
        assertThat(service.consume(ENCOUNTER, BillingItemTypes.RADIOLOGY, XRAY, "RADIOLOGY", nextOrderId++)).isEmpty();
        assertThat(anyLab.getQuantityUsed()).isEqualTo(1);
    }

    @Test
    void repeatLimitStopsTheSameTestButNotOthers() {
        addPackage(1L);
        PatientPackageItem anyLab = addItem(1L, 12L, BillingItemTypes.LABORATORY, null, 5, true, 1);

        assertThat(orderLab(GLUCOSE)).isPresent();
        assertThat(orderLab(GLUCOSE)).as("same test a second time, repeat = 1").isEmpty();
        assertThat(orderLab(HBA1C)).as("another test is still counted").isPresent();
        assertThat(anyLab.getQuantityUsed()).isEqualTo(2);
    }

    @Test
    void repeatTwoAllowsTheSameTestTwice() {
        addPackage(1L);
        addItem(1L, 12L, BillingItemTypes.LABORATORY, null, 8, true, 2);

        assertThat(orderLab(GLUCOSE)).isPresent();
        assertThat(orderLab(GLUCOSE)).isPresent();
        assertThat(orderLab(GLUCOSE)).as("third time").isEmpty();
    }

    @Test
    void ordersBeyondTheRoomLeftAreNotCountedAndThePackageCompletes() {
        PatientPackage pkg = addPackage(1L);
        PatientPackageItem anyLab = addItem(1L, 12L, BillingItemTypes.LABORATORY, null, 2, true, null);

        assertThat(orderLab(CBC)).isPresent();
        assertThat(orderLab(GLUCOSE)).isPresent();
        assertThat(orderLab(HBA1C)).as("third test, only 2 covered: charged as usual").isEmpty();
        assertThat(anyLab.getQuantityUsed()).isEqualTo(2);
        assertThat(pkg.getStatus()).isEqualTo(PatientPackageStatus.COMPLETED);
    }

    @Test
    void cancelledPackageIsNotUsed() {
        PatientPackage pkg = addPackage(1L);
        pkg.setStatus(PatientPackageStatus.CANCELLED);
        addItem(1L, 12L, BillingItemTypes.LABORATORY, null, 5, true, null);

        assertThat(orderLab(CBC)).isEmpty();
    }

    @Test
    void packageOfAnotherVisitIsNotUsed() {
        PatientPackage other = addPackage(1L);
        other.setEncounterId(999L);
        addItem(1L, 12L, BillingItemTypes.LABORATORY, null, 5, true, null);

        assertThat(orderLab(CBC)).isEmpty();
    }

    @Test
    void validityIsCheckedOnlyWhenThePackageIsGiven() {
        // The package setup may expire or be switched off after it was given: the patient's copy keeps working.
        // Ordering never reads the setup's dates or active flag, only the patient's package status and quantities.
        addPackage(1L);
        addItem(1L, 12L, BillingItemTypes.LABORATORY, null, 5, true, null);

        assertThat(orderLab(CBC)).isPresent();
    }

    // ---------- package panel: the line the doctor chose ----------

    @Test
    void panelCountsOnTheChosenCategoryLineEvenWhenAnItemLineExists() {
        addPackage(1L);
        PatientPackageItem cbcLine = addItem(1L, 11L, BillingItemTypes.LABORATORY, CBC, 1, false, null);
        PatientPackageItem anyLab = addItem(1L, 12L, BillingItemTypes.LABORATORY, null, 5, true, null);

        Optional<PatientPackageUsage> usage = service.consumeLine(anyLab.getId(), ENCOUNTER, CBC, "LABORATORY", nextOrderId++);

        assertThat(usage).isPresent();
        assertThat(anyLab.getQuantityUsed()).isEqualTo(1);
        assertThat(cbcLine.getQuantityUsed()).isZero();
    }

    @Test
    void panelLineWithoutRoomIsNotCountedElsewhere() {
        addPackage(1L);
        PatientPackageItem anyLab = addItem(1L, 12L, BillingItemTypes.LABORATORY, null, 1, true, null);
        PatientPackageItem cbcLine = addItem(1L, 11L, BillingItemTypes.LABORATORY, CBC, 1, false, null);

        service.consumeLine(anyLab.getId(), ENCOUNTER, GLUCOSE, "LABORATORY", nextOrderId++);
        Optional<PatientPackageUsage> second = service.consumeLine(anyLab.getId(), ENCOUNTER, CBC, "LABORATORY", nextOrderId++);

        assertThat(second).as("chosen line is full: charged as usual, not moved to the CBC line").isEmpty();
        assertThat(cbcLine.getQuantityUsed()).isZero();
    }

    @Test
    void panelRespectsTheRepeatLimitOfTheChosenLine() {
        addPackage(1L);
        PatientPackageItem anyLab = addItem(1L, 12L, BillingItemTypes.LABORATORY, null, 5, true, 1);

        assertThat(service.consumeLine(anyLab.getId(), ENCOUNTER, GLUCOSE, "LABORATORY", nextOrderId++)).isPresent();
        assertThat(service.consumeLine(anyLab.getId(), ENCOUNTER, GLUCOSE, "LABORATORY", nextOrderId++)).isEmpty();
    }

    @Test
    void automaticMatchingIsOffWhileThePanelOrders() {
        addPackage(1L);
        PatientPackageItem anyLab = addItem(1L, 12L, BillingItemTypes.LABORATORY, null, 5, true, null);
        com.dazzle.asklepios.domain.PatientServiceAndProduct line = new com.dazzle.asklepios.domain.PatientServiceAndProduct();
        line.setEncounterId(ENCOUNTER);
        line.setServiceSource(com.dazzle.asklepios.domain.enumeration.ServiceSource.SERVICE_AND_PRODUCT);
        line.setBillingItemType(BillingItemTypes.SERVICE);
        line.setServiceId(1501L);

        PatientPackageAutoApplyService.withoutAutoApply(() -> {
            service.onServiceLineCreated(line);
            return null;
        });

        assertThat(usages).isEmpty();
        assertThat(anyLab.getQuantityUsed()).isZero();
    }

    // ---------- UCC medication and vaccination ----------

    @Test
    void uccMedicationDoseIsTakenFromThePackage() {
        addPackage(1L);
        PatientPackageItem paracetamol = addItem(1L, 21L, BillingItemTypes.MEDICATION, 300L, 2, false, null);

        service.onServiceLineCreated(line(com.dazzle.asklepios.domain.enumeration.ServiceSource.URGENT_CARE_MEDICATION_ORDER,
                BillingItemTypes.MEDICATION, 300L));

        assertThat(paracetamol.getQuantityUsed()).isEqualTo(1);
    }

    @Test
    void prescriptionMedicationIsNotTakenFromThePackage() {
        addPackage(1L);
        PatientPackageItem paracetamol = addItem(1L, 21L, BillingItemTypes.MEDICATION, 300L, 2, false, null);

        service.onServiceLineCreated(line(com.dazzle.asklepios.domain.enumeration.ServiceSource.PRESCRIPTION,
                BillingItemTypes.MEDICATION, 300L));

        assertThat(paracetamol.getQuantityUsed()).isZero();
    }

    @Test
    void vaccinationIsTakenFromTheVaccineCategory() {
        addPackage(1L);
        PatientPackageItem anyVaccine = addItem(1L, 22L, BillingItemTypes.VACCINE, null, 1, true, null);

        service.onServiceLineCreated(line(com.dazzle.asklepios.domain.enumeration.ServiceSource.VACCINATION,
                BillingItemTypes.VACCINE, 77L));

        assertThat(anyVaccine.getQuantityUsed()).isEqualTo(1);
    }

    // ---------- helpers ----------

    private com.dazzle.asklepios.domain.PatientServiceAndProduct line(
            com.dazzle.asklepios.domain.enumeration.ServiceSource source, BillingItemTypes type, long itemId
    ) {
        com.dazzle.asklepios.domain.PatientServiceAndProduct l = new com.dazzle.asklepios.domain.PatientServiceAndProduct();
        l.setId(nextOrderId++);
        l.setEncounterId(ENCOUNTER);
        l.setServiceSource(source);
        l.setBillingItemType(type);
        if (type == BillingItemTypes.MEDICATION) {
            l.setBrandMedicationId(itemId);
        } else if (type == BillingItemTypes.VACCINE) {
            l.setVaccineBrandId(itemId);
        } else {
            l.setServiceId(itemId);
        }
        return l;
    }

    private Optional<PatientPackageUsage> orderLab(long testId) {
        return service.consume(ENCOUNTER, BillingItemTypes.LABORATORY, testId, "LABORATORY", nextOrderId++);
    }

    private PatientPackage addPackage(long id) {
        PatientPackage p = PatientPackage.builder()
                .id(id).patientId(10L).encounterId(ENCOUNTER).packageSetupId(1L)
                .packageName("PKG-" + id).status(PatientPackageStatus.ACTIVE).build();
        packages.put(id, p);
        return p;
    }

    private PatientPackageItem addItem(long pkgId, long id, BillingItemTypes type, Long sourceId, int qty, boolean category, Integer repeat) {
        PatientPackageItem it = PatientPackageItem.builder()
                .id(id).patientPackageId(pkgId).itemType(type).sourceId(sourceId)
                .quantityTotal(qty).quantityUsed(0).isCategory(category).maxRepeat(repeat).build();
        items.put(id, it);
        return it;
    }
}
