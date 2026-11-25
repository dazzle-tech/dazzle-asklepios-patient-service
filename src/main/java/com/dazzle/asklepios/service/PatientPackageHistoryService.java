package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientPackage;
import com.dazzle.asklepios.domain.PatientPackageItem;
import com.dazzle.asklepios.domain.PatientPackageUsage;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PatientPackageUsageStatus;
import com.dazzle.asklepios.repository.PatientPackageItemRepository;
import com.dazzle.asklepios.repository.PatientPackageRepository;
import com.dazzle.asklepios.repository.PatientPackageUsageRepository;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * History of one patient package: when it was given, and every order counted against it
 * (who, when, which item, from which screen) and returned when that order was cancelled.
 */
@Service
@Transactional(readOnly = true)
public class PatientPackageHistoryService {

    private static final String ENTITY = "patientPackage";

    private final PatientPackageRepository packageRepository;
    private final PatientPackageItemRepository itemRepository;
    private final PatientPackageUsageRepository usageRepository;
    private final EntityManager entityManager;

    public PatientPackageHistoryService(
            PatientPackageRepository packageRepository,
            PatientPackageItemRepository itemRepository,
            PatientPackageUsageRepository usageRepository,
            EntityManager entityManager
    ) {
        this.packageRepository = packageRepository;
        this.itemRepository = itemRepository;
        this.usageRepository = usageRepository;
        this.entityManager = entityManager;
    }

    /** One counted order. returnedBy / returnedDate are set when it was given back (status RETURNED). */
    public record UsageRowVM(
            Long id,
            Long itemId,
            BillingItemTypes itemType,
            Boolean fromCategory,
            String lineName,
            Long orderedItemId,
            String orderedItemCode,
            String orderedItemName,
            Integer quantity,
            String orderSource,
            Boolean fromPanel,
            Long orderId,
            String status,
            String countedBy,
            Instant countedDate,
            String returnedBy,
            Instant returnedDate
    ) implements Serializable {}

    public record HistoryVM(
            Long patientPackageId,
            String packageCode,
            String packageName,
            String status,
            String givenBy,
            Instant givenDate,
            String cancelReason,
            List<UsageRowVM> usages
    ) implements Serializable {}

    public HistoryVM history(Long patientPackageId) {
        PatientPackage pkg = packageRepository.findById(patientPackageId)
                .orElseThrow(() -> new NotFoundAlertException("Patient package not found with id " + patientPackageId, ENTITY, "notfound"));
        Map<Long, PatientPackageItem> items = itemRepository.findByPatientPackageIdOrderByIdAsc(pkg.getId()).stream()
                .collect(Collectors.toMap(PatientPackageItem::getId, Function.identity()));
        List<PatientPackageUsage> usages = usageRepository.findByPatientPackageIdOrderByIdDesc(pkg.getId());

        // names of the items actually ordered (a category line covers many different items)
        Map<BillingItemTypes, Set<Long>> idsByType = new EnumMap<>(BillingItemTypes.class);
        for (PatientPackageUsage u : usages) {
            PatientPackageItem item = items.get(u.getPatientPackageItemId());
            Long sourceId = u.getSourceId() != null ? u.getSourceId() : item != null ? item.getSourceId() : null;
            if (item != null && sourceId != null) {
                idsByType.computeIfAbsent(item.getItemType(), t -> new HashSet<>()).add(sourceId);
            }
        }
        Map<String, String[]> names = new HashMap<>();
        idsByType.forEach((type, ids) -> names.putAll(lookupNames(type, ids)));

        List<UsageRowVM> rows = new ArrayList<>();
        for (PatientPackageUsage u : usages) {
            PatientPackageItem item = items.get(u.getPatientPackageItemId());
            BillingItemTypes type = item != null ? item.getItemType() : null;
            boolean category = item != null && Boolean.TRUE.equals(item.getIsCategory());
            Long sourceId = u.getSourceId() != null ? u.getSourceId() : item != null ? item.getSourceId() : null;
            String[] codeAndName = type != null && sourceId != null ? names.get(type + ":" + sourceId) : null;
            boolean returned = u.getStatus() == PatientPackageUsageStatus.REVERSED;
            rows.add(new UsageRowVM(
                    u.getId(),
                    u.getPatientPackageItemId(),
                    type,
                    category,
                    item == null ? null : category ? "Any " + type.name().charAt(0) + type.name().substring(1).toLowerCase() : item.getItemName(),
                    sourceId,
                    codeAndName != null ? codeAndName[0] : item != null && !category ? item.getItemCode() : null,
                    codeAndName != null ? codeAndName[1] : item != null && !category ? item.getItemName() : null,
                    u.getQuantity(),
                    u.getOrderSource(),
                    Boolean.TRUE.equals(u.getFromPanel()),
                    u.getOrderId(),
                    returned ? "RETURNED" : "COUNTED",
                    u.getCreatedBy(),
                    u.getCreatedDate(),
                    returned ? u.getLastModifiedBy() : null,
                    returned ? u.getLastModifiedDate() : null
            ));
        }

        return new HistoryVM(
                pkg.getId(),
                pkg.getPackageCode(),
                pkg.getPackageName(),
                pkg.getStatus() != null ? pkg.getStatus().name() : null,
                pkg.getCreatedBy(),
                pkg.getCreatedDate(),
                pkg.getCancelReason(),
                rows
        );
    }

    /** "TYPE:id" -> {code, name} from the item's own setup table. */
    private Map<String, String[]> lookupNames(BillingItemTypes type, Collection<Long> ids) {
        String table = switch (type) {
            case LABORATORY, RADIOLOGY, PATHOLOGY -> "diagnostic_test";
            case SERVICE -> "service";
            case PROCEDURE -> "procedure";
            case MEDICATION -> "brand_medication";
            case VACCINE -> "vaccine_brands";
            default -> null;
        };
        if (table == null || ids.isEmpty()) {
            return Map.of();
        }
        String codeColumn = "diagnostic_test".equals(table) ? "internal_code" : "code";
        List<?> rows = entityManager
                .createNativeQuery("select t.id, t." + codeColumn + ", t.name from " + table + " t where t.id in (:ids)")
                .setParameter("ids", ids)
                .getResultList();
        Map<String, String[]> result = new HashMap<>();
        for (Object row : rows) {
            Object[] r = (Object[]) row;
            result.put(type + ":" + r[0], new String[] {
                    r[1] != null ? r[1].toString() : null,
                    r[2] != null ? r[2].toString() : null
            });
        }
        return result;
    }
}
