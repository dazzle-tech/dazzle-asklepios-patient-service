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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/** Cancelling (or the lab rejecting) an order that was counted gives the quantity back to its package line. */
class PatientPackageCancelScenarioTest {

    @Mock private PatientPackageUsageRepository usageRepository;
    @Mock private PatientPackageItemRepository itemRepository;
    @Mock private PatientPackageRepository packageRepository;

    private PatientPackageUsageReversalService service;
    private final List<PatientPackageUsage> usages = new ArrayList<>();
    private PatientPackage pkg;
    private PatientPackageItem anyLab;
    private PatientPackageItem serviceLine;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PatientPackageUsageReversalService(usageRepository, itemRepository, packageRepository);

        pkg = PatientPackage.builder().id(1L).encounterId(100L).status(PatientPackageStatus.ACTIVE).build();
        anyLab = PatientPackageItem.builder().id(11L).patientPackageId(1L).itemType(BillingItemTypes.LABORATORY)
                .isCategory(true).quantityTotal(2).quantityUsed(0).build();
        serviceLine = PatientPackageItem.builder().id(12L).patientPackageId(1L).itemType(BillingItemTypes.SERVICE)
                .sourceId(1501L).quantityTotal(1).quantityUsed(0).build();

        when(packageRepository.findById(1L)).thenReturn(Optional.of(pkg));
        when(itemRepository.findByIdForUpdate(anyLong())).thenAnswer(i ->
                Optional.ofNullable(((Long) i.getArgument(0)) == 11L ? anyLab : ((Long) i.getArgument(0)) == 12L ? serviceLine : null));
        when(usageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(usageRepository.findByOrderSourceInAndOrderIdAndStatus(anyCollection(), anyLong(), any())).thenAnswer(i -> {
            Collection<String> sources = i.getArgument(0);
            Long orderId = i.getArgument(1);
            PatientPackageUsageStatus status = i.getArgument(2);
            return usages.stream().filter(u -> sources.contains(u.getOrderSource())
                    && u.getOrderId().equals(orderId) && u.getStatus() == status).toList();
        });
    }

    private PatientPackageUsage used(PatientPackageItem item, String source, long orderId) {
        item.setQuantityUsed(item.getQuantityUsed() + 1);
        PatientPackageUsage u = PatientPackageUsage.builder().id(orderId).patientPackageId(1L).patientPackageItemId(item.getId())
                .quantity(1).orderSource(source).orderId(orderId).status(PatientPackageUsageStatus.USED).build();
        usages.add(u);
        return u;
    }

    @Test
    void cancelledTestGivesItsUnitBack() {
        PatientPackageUsage u = used(anyLab, "LABORATORY", 500L);

        service.onDiagnosticTestCancelled(500L);

        assertThat(u.getStatus()).isEqualTo(PatientPackageUsageStatus.REVERSED);
        assertThat(anyLab.getQuantityUsed()).isZero();
    }

    @Test
    void cancellingTwiceGivesBackOnlyOnce() {
        used(anyLab, "LABORATORY", 500L);
        used(anyLab, "LABORATORY", 501L);

        service.onDiagnosticTestCancelled(500L);
        service.onDiagnosticTestCancelled(500L);

        assertThat(anyLab.getQuantityUsed()).isEqualTo(1);
    }

    @Test
    void completedPackageOpensAgainAfterACancel() {
        used(anyLab, "LABORATORY", 500L);
        used(anyLab, "LABORATORY", 501L);
        used(serviceLine, "PACKAGE", 700L);
        pkg.setStatus(PatientPackageStatus.COMPLETED);

        service.onDiagnosticTestCancelled(501L);

        assertThat(pkg.getStatus()).isEqualTo(PatientPackageStatus.ACTIVE);
        assertThat(anyLab.getQuantityRemaining()).isEqualTo(1);
    }

    @Test
    void serviceFromThePanelOrFromTheServicesScreenGivesItsUnitBack() {
        used(serviceLine, "PACKAGE", 700L);
        service.onServiceCancelled(700L);
        assertThat(serviceLine.getQuantityUsed()).isZero();

        used(serviceLine, "SERVICE", 701L);
        service.onServiceCancelled(701L);
        assertThat(serviceLine.getQuantityUsed()).isZero();
    }

    @Test
    void orderThatWasNotCountedChangesNothing() {
        used(anyLab, "LABORATORY", 500L);

        service.onDiagnosticTestCancelled(999L);
        service.onProcedureCancelled(500L); // same id, other order type

        assertThat(anyLab.getQuantityUsed()).isEqualTo(1);
    }
}
