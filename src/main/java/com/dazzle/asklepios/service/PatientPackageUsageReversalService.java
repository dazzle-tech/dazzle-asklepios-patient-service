package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientPackage;
import com.dazzle.asklepios.domain.PatientPackageItem;
import com.dazzle.asklepios.domain.PatientPackageUsage;
import com.dazzle.asklepios.domain.enumeration.PatientPackageStatus;
import com.dazzle.asklepios.domain.enumeration.PatientPackageUsageStatus;
import com.dazzle.asklepios.repository.PatientPackageItemRepository;
import com.dazzle.asklepios.repository.PatientPackageRepository;
import com.dazzle.asklepios.repository.PatientPackageUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Gives the quantity back to the package when an order made from it is cancelled.
 * Depends on repositories only, so the order services can call it without a dependency cycle.
 */
@Service
@Transactional
public class PatientPackageUsageReversalService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPackageUsageReversalService.class);

    /** order_source values written by {@link PatientPackageOrderService}. */
    public static final String SOURCE_LABORATORY = "LABORATORY";
    public static final String SOURCE_RADIOLOGY = "RADIOLOGY";
    public static final String SOURCE_PATHOLOGY = "PATHOLOGY";
    public static final String SOURCE_PROCEDURE = "PROCEDURE";
    public static final String SOURCE_SERVICE = "PACKAGE";

    public static final Set<String> DIAGNOSTIC_SOURCES = Set.of(SOURCE_LABORATORY, SOURCE_RADIOLOGY, SOURCE_PATHOLOGY);

    private final PatientPackageUsageRepository usageRepository;
    private final PatientPackageItemRepository itemRepository;
    private final PatientPackageRepository packageRepository;

    public PatientPackageUsageReversalService(
            PatientPackageUsageRepository usageRepository,
            PatientPackageItemRepository itemRepository,
            PatientPackageRepository packageRepository
    ) {
        this.usageRepository = usageRepository;
        this.itemRepository = itemRepository;
        this.packageRepository = packageRepository;
    }

    /** A diagnostic order test was cancelled. */
    public void onDiagnosticTestCancelled(Long diagnosticOrderTestId) {
        reverse(DIAGNOSTIC_SOURCES, diagnosticOrderTestId);
    }

    /** A patient procedure was cancelled. */
    public void onProcedureCancelled(Long patientProcedureId) {
        reverse(Set.of(SOURCE_PROCEDURE), patientProcedureId);
    }

    /** A patient service/product line was cancelled. */
    public void onServiceCancelled(Long patientServiceAndProductId) {
        reverse(Set.of(SOURCE_SERVICE, PatientPackageAutoApplyService.SOURCE_SERVICE_LINE), patientServiceAndProductId);
    }

    private void reverse(Collection<String> sources, Long orderId) {
        if (orderId == null) {
            return;
        }
        List<PatientPackageUsage> usages =
                usageRepository.findByOrderSourceInAndOrderIdAndStatus(sources, orderId, PatientPackageUsageStatus.USED);
        for (PatientPackageUsage usage : usages) {
            usage.setStatus(PatientPackageUsageStatus.REVERSED);
            usageRepository.save(usage);

            PatientPackageItem item = itemRepository.findByIdForUpdate(usage.getPatientPackageItemId()).orElse(null);
            if (item != null) {
                int used = item.getQuantityUsed() == null ? 0 : item.getQuantityUsed();
                item.setQuantityUsed(Math.max(0, used - usage.getQuantity()));
                itemRepository.save(item);
            }

            PatientPackage pkg = packageRepository.findById(usage.getPatientPackageId()).orElse(null);
            if (pkg != null && pkg.getStatus() == PatientPackageStatus.COMPLETED) {
                pkg.setStatus(PatientPackageStatus.ACTIVE);
                packageRepository.save(pkg);
            }
            LOG.info("Package usage id={} reversed: order {} id={} cancelled, quantity {} given back",
                    usage.getId(), usage.getOrderSource(), orderId, usage.getQuantity());
        }
    }
}
