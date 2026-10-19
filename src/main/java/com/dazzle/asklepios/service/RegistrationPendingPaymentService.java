package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.BedClient;
import com.dazzle.asklepios.client.setup.DiagnosticTestClient;
import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.client.setup.dto.BedDTO;
import com.dazzle.asklepios.client.setup.dto.DiagnosticTestSetupDTO;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingChargeResponsibility;
import com.dazzle.asklepios.domain.EncounterAssignToBed;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingResponsibilityStatus;
import com.dazzle.asklepios.domain.enumeration.billing.ResponsiblePartyType;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.EncounterAssignToBedRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.RegistrationPendingPaymentSpecification;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.RegistrationPendingPaymentChangesDTO;
import com.dazzle.asklepios.service.dto.billing.RegistrationPendingPaymentEncounterDTO;
import com.dazzle.asklepios.service.dto.billing.RegistrationPendingPaymentItemDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RegistrationPendingPaymentService {

    private static final Logger LOG =
            LoggerFactory.getLogger(RegistrationPendingPaymentService.class);

    public static final String AWAITING_FINANCIAL_CALCULATION =
            "AWAITING_FINANCIAL_CALCULATION";

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_NEW_IDS = 100;

    private static final Set<BillingChargeLineStatus> EXCLUDED_LINE_STATUSES =
            EnumSet.of(
                    BillingChargeLineStatus.CANCELLED,
                    BillingChargeLineStatus.REVERSED
            );

    private static final Set<BillingResponsibilityStatus> EXCLUDED_RESP_STATUSES =
            EnumSet.of(
                    BillingResponsibilityStatus.CANCELLED,
                    BillingResponsibilityStatus.SUPERSEDED,
                    BillingResponsibilityStatus.REVERSED
            );

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final EntityManager entityManager;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientRepository patientRepository;
    private final EncounterAssignToBedRepository encounterAssignToBedRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;
    private final BillingChargeResponsibilityRepository billingChargeResponsibilityRepository;
    private final DiagnosticChargeTimingService diagnosticChargeTimingService;
    private final BillingItemDisplayNameService billingItemDisplayNameService;
    private final BedClient bedClient;
    private final DiagnosticTestClient diagnosticTestClient;
    private final ServiceClient serviceClient;

    public RegistrationPendingPaymentService(
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            EntityManager entityManager,
            PatientEncounterRepository patientEncounterRepository,
            PatientRepository patientRepository,
            EncounterAssignToBedRepository encounterAssignToBedRepository,
            BillingChargeLineRepository billingChargeLineRepository,
            BillingChargeResponsibilityRepository billingChargeResponsibilityRepository,
            DiagnosticChargeTimingService diagnosticChargeTimingService,
            BillingItemDisplayNameService billingItemDisplayNameService,
            BedClient bedClient,
            DiagnosticTestClient diagnosticTestClient,
            ServiceClient serviceClient
    ) {
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.entityManager = entityManager;
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientRepository = patientRepository;
        this.encounterAssignToBedRepository = encounterAssignToBedRepository;
        this.billingChargeLineRepository = billingChargeLineRepository;
        this.billingChargeResponsibilityRepository = billingChargeResponsibilityRepository;
        this.diagnosticChargeTimingService = diagnosticChargeTimingService;
        this.billingItemDisplayNameService = billingItemDisplayNameService;
        this.bedClient = bedClient;
        this.diagnosticTestClient = diagnosticTestClient;
        this.serviceClient = serviceClient;
    }

    public Page<RegistrationPendingPaymentEncounterDTO> searchRegistrationPendingItems(
            Long requestedFacilityId,
            String patientName,
            Pageable pageable
    ) {
        Long facilityId = resolveAuthorizedFacilityId(requestedFacilityId);
        Pageable safePageable = sanitizePageable(pageable);
        Specification<PatientServiceAndProduct> specification =
                pendingItemSpecification(facilityId, patientName);

        long totalEncounters = countDistinctEncounters(specification);
        if (totalEncounters == 0L) {
            return new PageImpl<>(List.of(), safePageable, 0);
        }

        List<Long> encounterIds = findEncounterIdsPage(specification, safePageable);
        if (encounterIds.isEmpty()) {
            return new PageImpl<>(List.of(), safePageable, totalEncounters);
        }

        List<PatientServiceAndProduct> items =
                patientServiceAndProductRepository.findAll(
                        specification.and(encounterIdIn(encounterIds)),
                        Sort.by(Sort.Direction.DESC, "id")
                );

        Map<Long, List<RegistrationPendingPaymentItemDTO>> itemsByEncounterId =
                new LinkedHashMap<>();
        for (Long encounterId : encounterIds) {
            itemsByEncounterId.put(encounterId, new ArrayList<>());
        }
        for (RegistrationPendingPaymentItemDTO item : toItemDtos(items)) {
            List<RegistrationPendingPaymentItemDTO> encounterItems =
                    itemsByEncounterId.get(item.encounterId());
            if (encounterItems != null) {
                encounterItems.add(item);
            }
        }

        List<RegistrationPendingPaymentEncounterDTO> content = new ArrayList<>();
        for (Long encounterId : encounterIds) {
            List<RegistrationPendingPaymentItemDTO> encounterItems =
                    itemsByEncounterId.get(encounterId);
            if (encounterItems == null || encounterItems.isEmpty()) {
                continue;
            }
            content.add(toEncounterDto(encounterId, encounterItems));
        }

        return new PageImpl<>(content, safePageable, totalEncounters);
    }

    public RegistrationPendingPaymentChangesDTO getRegistrationPendingItemChanges(
            Long requestedFacilityId,
            Long afterId
    ) {
        Long facilityId = resolveAuthorizedFacilityId(requestedFacilityId);
        long after = afterId == null || afterId < 0 ? 0L : afterId;

        Specification<PatientServiceAndProduct> specification =
                pendingItemSpecification(facilityId, null);

        long totalCount = patientServiceAndProductRepository.count(specification);

        Page<PatientServiceAndProduct> latestPage =
                patientServiceAndProductRepository.findAll(
                        specification,
                        PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id"))
                );
        Long latestId = latestPage.getContent().isEmpty()
                ? 0L
                : latestPage.getContent().get(0).getId();

        List<Long> newItemIds = List.of();
        if (after < latestId) {
            Specification<PatientServiceAndProduct> newItemsSpec =
                    specification.and(
                            RegistrationPendingPaymentSpecification.idGreaterThan(after)
                    );
            newItemIds =
                    patientServiceAndProductRepository.findAll(
                                    newItemsSpec,
                                    PageRequest.of(
                                            0,
                                            MAX_NEW_IDS,
                                            Sort.by(Sort.Direction.ASC, "id")
                                    )
                            )
                            .getContent()
                            .stream()
                            .map(PatientServiceAndProduct::getId)
                            .toList();
        }

        return new RegistrationPendingPaymentChangesDTO(
                after,
                latestId,
                totalCount,
                newItemIds
        );
    }

    private Specification<PatientServiceAndProduct> pendingItemSpecification(
            Long facilityId,
            String patientName
    ) {
        Specification<PatientServiceAndProduct> specification =
                RegistrationPendingPaymentSpecification.forFacility(facilityId);

        if (patientName != null && !patientName.isBlank()) {
            specification = specification.and(
                    RegistrationPendingPaymentSpecification.patientNameContains(patientName)
            );
        }

        return specification;
    }

    private static Specification<PatientServiceAndProduct> encounterIdIn(
            Collection<Long> encounterIds
    ) {
        return (root, query, cb) -> root.get("encounterId").in(encounterIds);
    }

    private long countDistinctEncounters(
            Specification<PatientServiceAndProduct> specification
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<PatientServiceAndProduct> root = query.from(PatientServiceAndProduct.class);
        query.select(cb.countDistinct(root.get("encounterId")));
        applySpecification(specification, root, query, cb);
        Long total = entityManager.createQuery(query).getSingleResult();
        return total == null ? 0L : total;
    }

    private List<Long> findEncounterIdsPage(
            Specification<PatientServiceAndProduct> specification,
            Pageable pageable
    ) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<PatientServiceAndProduct> root = query.from(PatientServiceAndProduct.class);
        Path<Long> encounterId = root.get("encounterId");
        Expression<Instant> newestCreatedDate =
                cb.greatest(root.<Instant>get("createdDate"));
        Expression<Long> newestItemId = cb.max(root.<Long>get("id"));

        query.multiselect(encounterId, newestCreatedDate, newestItemId);
        applySpecification(specification, root, query, cb);
        query.groupBy(encounterId);
        query.orderBy(
                cb.desc(newestCreatedDate),
                cb.desc(newestItemId),
                cb.desc(encounterId)
        );

        long offset = pageable.getOffset();
        List<Tuple> page = entityManager.createQuery(query)
                .setFirstResult(offset > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) offset)
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        List<Long> encounterIds = new ArrayList<>(page.size());
        for (Tuple tuple : page) {
            Object value = tuple.get(0);
            if (value instanceof Number number) {
                encounterIds.add(number.longValue());
            }
        }
        return encounterIds;
    }

    private static void applySpecification(
            Specification<PatientServiceAndProduct> specification,
            Root<PatientServiceAndProduct> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb
    ) {
        Predicate predicate = specification == null
                ? null
                : specification.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
    }

    private RegistrationPendingPaymentEncounterDTO toEncounterDto(
            Long encounterId,
            List<RegistrationPendingPaymentItemDTO> items
    ) {
        RegistrationPendingPaymentItemDTO header = items.get(0);
        boolean collectible = false;
        for (RegistrationPendingPaymentItemDTO item : items) {
            if (item.collectible()) {
                collectible = true;
                break;
            }
        }

        return new RegistrationPendingPaymentEncounterDTO(
                encounterId,
                header.encounterNumber(),
                header.patientId(),
                header.patientName(),
                header.patient(),
                header.bedId(),
                header.bedName(),
                items.size(),
                collectible,
                List.copyOf(items)
        );
    }

    private List<RegistrationPendingPaymentItemDTO> toItemDtos(
            List<PatientServiceAndProduct> items
    ) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        Set<Long> encounterIds = items.stream()
                .map(PatientServiceAndProduct::getEncounterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> patientIds = items.stream()
                .map(PatientServiceAndProduct::getPatientId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> pspIds = items.stream()
                .map(PatientServiceAndProduct::getId)
                .collect(Collectors.toSet());

        Map<Long, PatientEncounter> encountersById =
                patientEncounterRepository.findAllById(encounterIds).stream()
                        .collect(Collectors.toMap(PatientEncounter::getId, Function.identity()));

        Map<Long, Patient> patientsById =
                patientRepository.findAllById(patientIds).stream()
                        .collect(Collectors.toMap(Patient::getId, Function.identity()));

        Map<Long, EncounterAssignToBed> bedByEncounterId =
                encounterAssignToBedRepository
                        .findAllByEncounter_IdInAndIsActiveTrue(new ArrayList<>(encounterIds))
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        assignment -> assignment.getEncounter().getId(),
                                        Function.identity(),
                                        (left, right) -> left
                                )
                        );

        Map<Long, String> bedNamesById = loadBedNames(
                bedByEncounterId.values().stream()
                        .map(EncounterAssignToBed::getBedId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        );

        Map<Long, BillingChargeLine> chargeLineByPspId = loadChargeLines(pspIds);
        Map<Long, BillingChargeResponsibility> patientResponsibilityByPspId =
                loadPatientResponsibilities(pspIds);

        Map<String, String> displayNameCache = preloadDisplayNames(items);

        List<RegistrationPendingPaymentItemDTO> result = new ArrayList<>(items.size());
        for (PatientServiceAndProduct item : items) {
            result.add(
                    buildItemDto(
                            item,
                            encountersById.get(item.getEncounterId()),
                            patientsById.get(item.getPatientId()),
                            bedByEncounterId.get(item.getEncounterId()),
                            bedNamesById,
                            chargeLineByPspId.get(item.getId()),
                            patientResponsibilityByPspId.get(item.getId()),
                            displayNameCache
                    )
            );
        }
        return result;
    }

    private RegistrationPendingPaymentItemDTO buildItemDto(
            PatientServiceAndProduct item,
            PatientEncounter encounter,
            Patient patient,
            EncounterAssignToBed bedAssignment,
            Map<Long, String> bedNamesById,
            BillingChargeLine chargeLine,
            BillingChargeResponsibility patientResponsibility,
            Map<String, String> displayNameCache
    ) {
        boolean unknownAmounts =
                isUnknownFinancialAmounts(item);
        boolean deferred = diagnosticChargeTimingService.isChargeDeferred(item);
        boolean awaitingPreAuth =
                item.getPaymentStatus() == PaymentStatus.SKIPPED_PENDING_PRE_AUTH
                        || item.getPaymentStatus() == PaymentStatus.INSURANCE_PENDING;

        boolean amountsAvailable = !unknownAmounts;
        boolean hasActiveCharge = chargeLine != null;

        BigDecimal patientShare = null;
        BigDecimal insuranceShare = null;
        BigDecimal patientRemaining = null;

        if (amountsAvailable) {
            if (hasActiveCharge) {
                patientShare = money(chargeLine.getPatientResponsibilityAmount());
                insuranceShare = money(chargeLine.getInsuranceResponsibilityAmount());
                patientRemaining = patientResponsibility != null
                        ? money(patientResponsibility.getOutstandingAmount())
                        : money(item.getRemainingAmount());
            } else {
                patientShare = money(item.getPatientShareAmount());
                insuranceShare = money(item.getInsuranceShareAmount());
                patientRemaining = money(item.getRemainingAmount());
            }
        }

        boolean collectible =
                amountsAvailable
                        && hasActiveCharge
                        && !deferred
                        && !awaitingPreAuth
                        && patientRemaining != null
                        && patientRemaining.signum() > 0
                        && (item.getPaymentStatus() == PaymentStatus.PENDING
                        || item.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID
                        || item.getPaymentStatus() == PaymentStatus.RESERVED
                        || item.getPaymentStatus() == PaymentStatus.PARTIALLY_RESERVED
                        || item.getPaymentStatus() == PaymentStatus.DEBIT
                        || item.getPaymentStatus() == PaymentStatus.PARTIALLY_DEBIT);

        boolean awaitingFinancial =
                !collectible
                        && (unknownAmounts || deferred || awaitingPreAuth || !hasActiveCharge);

        String displayStatus = awaitingFinancial
                ? AWAITING_FINANCIAL_CALCULATION
                : (item.getPaymentStatus() == null
                ? PaymentStatus.PENDING.name()
                : item.getPaymentStatus().name());

        String itemName =
                billingItemDisplayNameService.resolveDisplayName(
                        item.getBillingItemType(),
                        item,
                        resolveCatalogId(item),
                        item.getNotes(),
                        displayNameCache
                );

        Long bedId = bedAssignment == null ? null : bedAssignment.getBedId();
        String bedName = bedId == null ? null : bedNamesById.get(bedId);

        return new RegistrationPendingPaymentItemDTO(
                item.getId(),
                item.getPatientId(),
                item.getEncounterId(),
                encounter == null ? null : encounter.getEncounterNumber(),
                buildPatientName(patient),
                toPatientMap(patient),
                bedId,
                bedName,
                item.getServiceSource(),
                item.getBillingItemType() == null ? null : item.getBillingItemType().name(),
                itemName,
                patientShare,
                insuranceShare,
                patientRemaining,
                item.getCurrency(),
                item.getPaymentStatus(),
                displayStatus,
                amountsAvailable,
                collectible,
                item.getCreatedDate()
        );
    }

    private Map<Long, BillingChargeLine> loadChargeLines(Collection<Long> pspIds) {
        if (pspIds == null || pspIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BillingChargeLine> byPspId = new HashMap<>();
        for (BillingChargeLine line :
                billingChargeLineRepository.findAllByPatientServiceProduct_IdInAndStatusNotInOrderByIdAsc(
                        pspIds,
                        EXCLUDED_LINE_STATUSES
                )) {
            Long pspId = line.getPatientServiceProduct() == null
                    ? null
                    : line.getPatientServiceProduct().getId();
            if (pspId != null) {
                byPspId.putIfAbsent(pspId, line);
            }
        }
        return byPspId;
    }

    private Map<Long, BillingChargeResponsibility> loadPatientResponsibilities(
            Collection<Long> pspIds
    ) {
        if (pspIds == null || pspIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BillingChargeResponsibility> byPspId = new HashMap<>();
        for (BillingChargeResponsibility responsibility :
                billingChargeResponsibilityRepository.findAllByPatientServiceProduct_IdInAndResponsiblePartyTypeAndStatusNotInOrderByIdAsc(
                        pspIds,
                        ResponsiblePartyType.PATIENT,
                        EXCLUDED_RESP_STATUSES
                )) {
            Long pspId = responsibility.getPatientServiceProduct() == null
                    ? null
                    : responsibility.getPatientServiceProduct().getId();
            if (pspId != null) {
                byPspId.putIfAbsent(pspId, responsibility);
            }
        }
        return byPspId;
    }

    private Map<String, String> preloadDisplayNames(List<PatientServiceAndProduct> items) {
        Map<String, String> cache = new HashMap<>();

        List<Long> diagnosticIds = items.stream()
                .filter(item -> isDiagnosticType(item.getBillingItemType()))
                .map(this::resolveCatalogId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<Long> serviceIds = items.stream()
                .filter(item -> item.getBillingItemType() == BillingItemTypes.SERVICE)
                .map(this::resolveCatalogId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (!diagnosticIds.isEmpty()) {
            try {
                List<DiagnosticTestSetupDTO> tests =
                        diagnosticTestClient.getDiagnosticTestsByIds(diagnosticIds);
                if (tests != null) {
                    for (DiagnosticTestSetupDTO test : tests) {
                        if (test == null || test.id() == null) {
                            continue;
                        }
                        String name = test.name() == null || test.name().isBlank()
                                ? "-"
                                : test.name().trim();
                        cache.put(BillingItemTypes.LABORATORY.name() + ":" + test.id(), name);
                        cache.put(BillingItemTypes.RADIOLOGY.name() + ":" + test.id(), name);
                        cache.put(BillingItemTypes.PATHOLOGY.name() + ":" + test.id(), name);
                    }
                }
            } catch (RuntimeException ex) {
                LOG.debug("Unable to batch-load diagnostic test names: {}", ex.getMessage());
            }
        }

        if (!serviceIds.isEmpty()) {
            try {
                List<ServiceSetupDTO> services = serviceClient.getServicesByIds(serviceIds);
                if (services != null) {
                    for (ServiceSetupDTO service : services) {
                        if (service == null || service.id() == null) {
                            continue;
                        }
                        String name = service.name() == null || service.name().isBlank()
                                ? "-"
                                : service.name().trim();
                        cache.put(BillingItemTypes.SERVICE.name() + ":" + service.id(), name);
                    }
                }
            } catch (RuntimeException ex) {
                LOG.debug("Unable to batch-load service names: {}", ex.getMessage());
            }
        }

        return cache;
    }

    private Map<Long, String> loadBedNames(List<Long> bedIds) {
        if (bedIds == null || bedIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<BedDTO> beds = bedClient.findBedsByIds(bedIds);
            if (beds == null) {
                return Map.of();
            }
            Map<Long, String> names = new HashMap<>();
            for (BedDTO bed : beds) {
                if (bed == null || bed.id() == null) {
                    continue;
                }
                names.put(
                        bed.id(),
                        bed.name() == null || bed.name().isBlank() ? null : bed.name().trim()
                );
            }
            return names;
        } catch (RuntimeException ex) {
            LOG.debug("Unable to batch-load bed names: {}", ex.getMessage());
            return Map.of();
        }
    }

    private Long resolveCatalogId(PatientServiceAndProduct item) {
        if (item == null) {
            return null;
        }
        if (item.getDiagnosticTestId() != null) {
            return item.getDiagnosticTestId();
        }
        if (item.getServiceId() != null) {
            return item.getServiceId();
        }
        return item.getSourceId();
    }

    private boolean isDiagnosticType(BillingItemTypes type) {
        return type == BillingItemTypes.LABORATORY
                || type == BillingItemTypes.RADIOLOGY
                || type == BillingItemTypes.PATHOLOGY;
    }

    private String buildPatientName(Patient patient) {
        if (patient == null) {
            return null;
        }
        return String.join(
                " ",
                java.util.stream.Stream.of(
                                patient.getFirstName(),
                                patient.getSecondName(),
                                patient.getThirdName(),
                                patient.getLastName()
                        )
                        .filter(part -> part != null && !part.isBlank())
                        .map(String::trim)
                        .toList()
        );
    }

    private Map<String, Object> toPatientMap(Patient patient) {
        if (patient == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", patient.getId());
        map.put("patientId", patient.getId());
        map.put("firstName", patient.getFirstName());
        map.put("secondName", patient.getSecondName());
        map.put("thirdName", patient.getThirdName());
        map.put("lastName", patient.getLastName());
        map.put("medicalRecordNumber", patient.getMedicalRecordNumber());
        map.put("primaryMobileNumber", patient.getPrimaryMobileNumber());
        return map;
    }

    private Long resolveAuthorizedFacilityId(Long requestedFacilityId) {
        Long jwtFacilityId = SecurityUtils.getCurrentUserFacility()
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Missing mandatory claim 'tenant' in JWT."
                        )
                );

        if (requestedFacilityId == null) {
            throw new BadRequestAlertException(
                    "Facility is required",
                    "registrationPendingPayment",
                    "facility.required"
            );
        }

        if (!jwtFacilityId.equals(requestedFacilityId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Requested facility does not match the authorized facility."
            );
        }

        return jwtFacilityId;
    }

    private Pageable sanitizePageable(Pageable pageable) {
        int page = pageable == null ? 0 : Math.max(pageable.getPageNumber(), 0);
        int size = pageable == null ? 15 : pageable.getPageSize();
        if (size < 1) {
            size = 15;
        }
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }

        return PageRequest.of(page, size);
    }

    private static boolean isUnknownFinancialAmounts(PatientServiceAndProduct item) {
        return money(item.getUnitPrice()).signum() == 0
                && money(item.getNetAmount()).signum() == 0
                && money(item.getPatientShareAmount()).signum() == 0
                && money(item.getInsuranceShareAmount()).signum() == 0
                && money(item.getRemainingAmount()).signum() == 0;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}