package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientPackage;
import com.dazzle.asklepios.domain.PatientPackageItem;
import com.dazzle.asklepios.domain.PatientPackageUsage;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PatientPackageStatus;
import com.dazzle.asklepios.domain.enumeration.PatientPackageUsageStatus;
import com.dazzle.asklepios.repository.PatientPackageItemRepository;
import com.dazzle.asklepios.repository.PatientPackageRepository;
import com.dazzle.asklepios.repository.PatientPackageUsageRepository;
import com.dazzle.asklepios.service.PatientPackageHistoryService.HistoryVM;
import com.dazzle.asklepios.service.PatientPackageHistoryService.UsageRowVM;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** The package history shows who gave the package, and every counted / returned order with the real item. */
class PatientPackageHistoryScenarioTest {

    @Mock private PatientPackageRepository packageRepository;
    @Mock private PatientPackageItemRepository itemRepository;
    @Mock private PatientPackageUsageRepository usageRepository;
    @Mock private EntityManager entityManager;
    @Mock private Query query;

    private PatientPackageHistoryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PatientPackageHistoryService(packageRepository, itemRepository, usageRepository, entityManager);

        PatientPackage pkg = PatientPackage.builder().id(1L).packageCode("PKG-LAB5").packageName("Lab 50% x5")
                .status(PatientPackageStatus.ACTIVE).build();
        pkg.setCreatedBy("reception");
        pkg.setCreatedDate(Instant.parse("2026-11-23T08:00:00Z"));

        PatientPackageItem anyLab = PatientPackageItem.builder().id(11L).patientPackageId(1L)
                .itemType(BillingItemTypes.LABORATORY).isCategory(true).quantityTotal(5).quantityUsed(1).build();

        PatientPackageUsage counted = usage(100L, 22L, PatientPackageUsageStatus.USED, "doctor", null);
        PatientPackageUsage returned = usage(101L, 4L, PatientPackageUsageStatus.REVERSED, "doctor", "nurse");

        when(packageRepository.findById(1L)).thenReturn(Optional.of(pkg));
        when(itemRepository.findByPatientPackageIdOrderByIdAsc(1L)).thenReturn(List.of(anyLab));
        when(usageRepository.findByPatientPackageIdOrderByIdDesc(1L)).thenReturn(List.of(returned, counted));
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(eq("ids"), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(
                new Object[] { 22L, "GLU", "Glucose" },
                new Object[] { 4L, "CBC", "CBC" }
        ));
    }

    private static PatientPackageUsage usage(long orderId, long testId, PatientPackageUsageStatus status, String by, String returnedBy) {
        PatientPackageUsage u = PatientPackageUsage.builder().id(orderId).patientPackageId(1L).patientPackageItemId(11L)
                .sourceId(testId).quantity(1).orderSource("LABORATORY").orderId(orderId).status(status).build();
        u.setCreatedBy(by);
        u.setCreatedDate(Instant.parse("2026-11-23T09:00:00Z"));
        u.setLastModifiedBy(returnedBy != null ? returnedBy : by);
        u.setLastModifiedDate(Instant.parse("2026-11-23T10:00:00Z"));
        return u;
    }

    @Test
    void showsWhoGaveThePackageAndEachOrderWithTheTestActuallyOrdered() {
        HistoryVM history = service.history(1L);

        assertThat(history.givenBy()).isEqualTo("reception");
        assertThat(history.usages()).hasSize(2);

        UsageRowVM returned = history.usages().get(0);
        assertThat(returned.status()).isEqualTo("RETURNED");
        assertThat(returned.orderedItemName()).isEqualTo("CBC");
        assertThat(returned.lineName()).isEqualTo("Any Laboratory");
        assertThat(returned.returnedBy()).isEqualTo("nurse");

        UsageRowVM counted = history.usages().get(1);
        assertThat(counted.status()).isEqualTo("COUNTED");
        assertThat(counted.orderedItemName()).isEqualTo("Glucose");
        assertThat(counted.orderedItemCode()).isEqualTo("GLU");
        assertThat(counted.countedBy()).isEqualTo("doctor");
        assertThat(counted.returnedBy()).isNull();
    }
}
