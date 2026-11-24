package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PackageSetupItemView;
import com.dazzle.asklepios.domain.PackageSetupView;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientPackage;
import com.dazzle.asklepios.domain.PatientPackageItem;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.repository.PackageSetupItemViewRepository;
import com.dazzle.asklepios.repository.PackageSetupViewRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientPackageItemRepository;
import com.dazzle.asklepios.repository.PatientPackageRepository;
import com.dazzle.asklepios.repository.PatientPackageUsageRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageVMs.AssignVM;
import com.dazzle.asklepios.web.rest.vm.patientPackage.PatientPackageVMs.ResponseVM;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Giving a package to a patient in a visit: active, validity, departments and inactive items. */
class PatientPackageAssignScenarioTest {

    private static final long PATIENT = 10L;
    private static final long ENCOUNTER = 100L;
    private static final long SETUP = 1L;
    private static final long OUT_PATIENT = 5000L;
    private static final long EMERGENCY = 5003L;

    @Mock private PatientPackageRepository patientPackageRepository;
    @Mock private PatientPackageItemRepository patientPackageItemRepository;
    @Mock private PatientPackageUsageRepository patientPackageUsageRepository;
    @Mock private PackageSetupViewRepository packageSetupViewRepository;
    @Mock private PackageSetupItemViewRepository packageSetupItemViewRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private PatientEncounterRepository patientEncounterRepository;
    @Mock private EntityManager entityManager;
    @Mock private Query departmentsQuery;

    private PatientPackageService service;
    private PackageSetupView setup;
    private final List<PackageSetupItemView> setupItems = new ArrayList<>();
    private final List<Object> allowedDepartments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PatientPackageService(
                patientPackageRepository, patientPackageItemRepository, patientPackageUsageRepository,
                packageSetupViewRepository, packageSetupItemViewRepository, patientRepository,
                patientEncounterRepository, entityManager);

        Patient patient = new Patient();
        patient.setId(PATIENT);
        PatientEncounter encounter = new PatientEncounter();
        encounter.setId(ENCOUNTER);
        encounter.setPatient(patient);
        encounter.setDepartmentId(OUT_PATIENT);

        setup = setupView(true, null, null, true);
        setupItems.add(setupItem(11L, BillingItemTypes.LABORATORY, null, 5, true, true, 1));
        setupItems.add(setupItem(12L, BillingItemTypes.RADIOLOGY, 1L, 1, false, true, null));

        when(patientRepository.existsById(PATIENT)).thenReturn(true);
        when(patientEncounterRepository.findById(ENCOUNTER)).thenReturn(Optional.of(encounter));
        when(patientPackageRepository.existsByEncounterIdAndPackageSetupIdAndStatusNot(anyLong(), anyLong(), any())).thenReturn(false);
        when(packageSetupViewRepository.findById(SETUP)).thenAnswer(i -> Optional.of(setup));
        when(packageSetupItemViewRepository.findByPackageSetupIdOrderByIdAsc(SETUP)).thenReturn(setupItems);
        when(entityManager.createNativeQuery(anyString())).thenReturn(departmentsQuery);
        when(departmentsQuery.setParameter(eq("id"), any())).thenReturn(departmentsQuery);
        when(departmentsQuery.getResultList()).thenReturn(allowedDepartments);
        when(patientPackageRepository.saveAndFlush(any(PatientPackage.class))).thenAnswer(i -> {
            PatientPackage p = i.getArgument(0);
            p.setId(500L);
            return p;
        });
        when(patientPackageItemRepository.saveAllAndFlush(any())).thenAnswer(i -> new ArrayList<PatientPackageItem>(i.getArgument(0)));
    }

    private ResponseVM assign() {
        return service.assign(new AssignVM(PATIENT, ENCOUNTER, SETUP));
    }

    @Test
    void activeValidPackageForAllDepartmentsIsGiven() {
        ResponseVM result = assign();

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).isCategory()).isTrue();
        assertThat(result.items().get(0).maxRepeat()).isEqualTo(1);
        assertThat(result.items().get(0).discountPercent()).isEqualByComparingTo("50");
    }

    @Test
    void inactivePackageIsRefused() {
        setup = setupView(false, null, null, true);

        assertThatThrownBy(this::assign).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("not active");
    }

    @Test
    void expiredPackageIsRefused() {
        setup = setupView(true, LocalDate.now().minusDays(30), LocalDate.now().minusDays(1), true);

        assertThatThrownBy(this::assign).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("not valid today");
    }

    @Test
    void packageNotStartedYetIsRefused() {
        setup = setupView(true, LocalDate.now().plusDays(1), null, true);

        assertThatThrownBy(this::assign).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("not valid today");
    }

    @Test
    void lastValidDayIsAccepted() {
        setup = setupView(true, LocalDate.now().minusDays(10), LocalDate.now(), true);

        assertThat(assign().items()).hasSize(2);
    }

    @Test
    void packageForAnotherDepartmentIsRefused() {
        setup = setupView(true, null, null, false);
        allowedDepartments.add(EMERGENCY);

        assertThatThrownBy(this::assign).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("department");
    }

    @Test
    void packageForTheVisitDepartmentIsGiven() {
        setup = setupView(true, null, null, false);
        allowedDepartments.add(EMERGENCY);
        allowedDepartments.add(OUT_PATIENT);

        assertThat(assign().items()).hasSize(2);
    }

    @Test
    void inactiveItemsAreLeftOut() {
        setupItems.add(setupItem(13L, BillingItemTypes.SERVICE, 1501L, 1, false, false, null));

        ResponseVM result = assign();

        assertThat(result.items()).extracting(i -> i.packageSetupItemId()).containsExactly(11L, 12L);
    }

    @Test
    void packageWithOnlyInactiveItemsIsRefused() {
        setupItems.clear();
        setupItems.add(setupItem(13L, BillingItemTypes.SERVICE, 1501L, 1, false, false, null));

        assertThatThrownBy(this::assign).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("no active items");
    }

    @Test
    void theSamePackageTwiceInOneVisitIsRefused() {
        when(patientPackageRepository.existsByEncounterIdAndPackageSetupIdAndStatusNot(anyLong(), anyLong(), any())).thenReturn(true);

        assertThatThrownBy(this::assign).isInstanceOf(BadRequestAlertException.class).hasMessageContaining("already added");
    }

    @Test
    void visitOfAnotherPatientIsRefused() {
        when(patientRepository.existsById(PATIENT + 1)).thenReturn(true);

        assertThatThrownBy(() -> service.assign(new AssignVM(PATIENT + 1, ENCOUNTER, SETUP)))
                .isInstanceOf(BadRequestAlertException.class).hasMessageContaining("does not belong");
    }

    @Test
    void packageWithoutAPriceIsGiven() {
        // a package without a fixed price (its items carry discounts) is given like any other
        assertThat(assign().packagePrice()).isNull();
    }

    // ---------- builders (the views are read-only, so fields are set directly) ----------

    private static PackageSetupView setupView(boolean active, LocalDate from, LocalDate to, boolean allDepartments) {
        PackageSetupView v = new PackageSetupView();
        ReflectionTestUtils.setField(v, "id", SETUP);
        ReflectionTestUtils.setField(v, "code", "PKG-T");
        ReflectionTestUtils.setField(v, "name", "Test package");
        ReflectionTestUtils.setField(v, "isActive", active);
        ReflectionTestUtils.setField(v, "validFrom", from);
        ReflectionTestUtils.setField(v, "validTo", to);
        ReflectionTestUtils.setField(v, "allDepartments", allDepartments);
        return v;
    }

    private static PackageSetupItemView setupItem(
            long id, BillingItemTypes type, Long sourceId, int qty, boolean category, boolean active, Integer repeat
    ) {
        PackageSetupItemView v = new PackageSetupItemView();
        ReflectionTestUtils.setField(v, "id", id);
        ReflectionTestUtils.setField(v, "packageSetupId", SETUP);
        ReflectionTestUtils.setField(v, "itemType", type);
        ReflectionTestUtils.setField(v, "sourceId", sourceId);
        ReflectionTestUtils.setField(v, "quantity", qty);
        ReflectionTestUtils.setField(v, "isActive", active);
        ReflectionTestUtils.setField(v, "isCategory", category);
        ReflectionTestUtils.setField(v, "maxRepeat", repeat);
        ReflectionTestUtils.setField(v, "discountPercent", category ? new BigDecimal("50") : null);
        return v;
    }
}
