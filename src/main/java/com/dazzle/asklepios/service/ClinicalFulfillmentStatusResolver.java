package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Consultation;
import com.dazzle.asklepios.domain.DentalProcedure;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.repository.ConsultationRepository;
import com.dazzle.asklepios.repository.DentalProcedureRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.EncounterBillingItemSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only clinical/fulfillment status for ledger service lines.
 * Diagnostic tests expose {@code processingStatus} only.
 * Does not participate in billing amounts, charge status, or invoice logic.
 */
@Service
@Transactional(readOnly = true)
public class ClinicalFulfillmentStatusResolver {

    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final PatientProcedureRepository patientProcedureRepository;
    private final PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository;
    private final ConsultationRepository consultationRepository;
    private final DentalProcedureRepository dentalProcedureRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    public ClinicalFulfillmentStatusResolver(
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            PatientProcedureRepository patientProcedureRepository,
            PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository,
            ConsultationRepository consultationRepository,
            DentalProcedureRepository dentalProcedureRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository
    ) {
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.patientProcedureRepository = patientProcedureRepository;
        this.patientPrescriptionMedicationRepository = patientPrescriptionMedicationRepository;
        this.consultationRepository = consultationRepository;
        this.dentalProcedureRepository = dentalProcedureRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
    }

    public void attachToItems(List<PatientServiceAndProduct> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Map<String, String> statuses = resolve(toLookups(items));
        for (PatientServiceAndProduct item : items) {
            item.setClinicalStatus(statuses.get(key(toLookup(item))));
        }
    }

    public List<EncounterBillingItemSummary> attachToSummaries(
            List<EncounterBillingItemSummary> items
    ) {
        return attachToSummaries(items, null);
    }

    public List<EncounterBillingItemSummary> attachToSummaries(
            List<EncounterBillingItemSummary> items,
            Long encounterId
    ) {
        if (items == null || items.isEmpty()) {
            return items;
        }

        Map<Long, PatientServiceAndProduct> pspById = loadPspItems(items);
        List<StatusLookup> lookups = new ArrayList<>();
        for (EncounterBillingItemSummary item : items) {
            lookups.add(toLookup(item, encounterId, pspById));
        }

        Map<String, String> statuses = resolve(lookups);
        return items.stream()
                .map(item -> item.withClinicalStatus(
                        statuses.get(key(toLookup(item, encounterId, pspById)))
                ))
                .toList();
    }

    private Map<String, String> resolve(Collection<StatusLookup> lookups) {
        Map<String, String> statuses = new HashMap<>();
        if (lookups == null || lookups.isEmpty()) {
            return statuses;
        }

        Set<Long> procedureIds = new HashSet<>();
        Set<Long> medicationIds = new HashSet<>();
        Set<Long> consultationIds = new HashSet<>();
        Set<Long> dentalIds = new HashSet<>();

        for (StatusLookup lookup : lookups) {
            if (lookup == null || lookup.sourceId() == null || isDiagnostic(lookup)) {
                continue;
            }
            switch (resolvedSource(lookup)) {
                case PROCEDURE -> procedureIds.add(lookup.sourceId());
                case PRESCRIPTION -> medicationIds.add(lookup.sourceId());
                case CONSULTATION_PORTAL -> consultationIds.add(lookup.sourceId());
                case DENTAL_PROCEDURE -> dentalIds.add(lookup.sourceId());
                default -> {
                }
            }
        }

        Map<String, String> diagnosticStatuses = loadDiagnosticProcessingStatuses(lookups);
        Map<Long, String> procedureStatuses = loadProcedureStatuses(procedureIds);
        Map<Long, String> medicationStatuses = loadMedicationStatuses(medicationIds);
        Map<Long, String> consultationStatuses = loadConsultationStatuses(consultationIds);
        Map<Long, String> dentalStatuses = loadDentalStatuses(dentalIds);

        for (StatusLookup lookup : lookups) {
            if (lookup == null) {
                continue;
            }
            String status;
            if (isDiagnostic(lookup)) {
                status = diagnosticStatuses.get(key(lookup));
            } else if (lookup.sourceId() == null) {
                continue;
            } else {
                status = switch (resolvedSource(lookup)) {
                    case PROCEDURE -> procedureStatuses.get(lookup.sourceId());
                    case PRESCRIPTION -> medicationStatuses.get(lookup.sourceId());
                    case CONSULTATION_PORTAL -> consultationStatuses.get(lookup.sourceId());
                    case DENTAL_PROCEDURE -> dentalStatuses.get(lookup.sourceId());
                    default -> null;
                };
            }
            if (status != null) {
                statuses.put(key(lookup), status);
            }
        }

        return statuses;
    }

    private Map<String, String> loadDiagnosticProcessingStatuses(Collection<StatusLookup> lookups) {
        Map<String, String> statuses = new HashMap<>();
        List<StatusLookup> diagnosticLookups = lookups.stream()
                .filter(this::isDiagnostic)
                .toList();
        if (diagnosticLookups.isEmpty()) {
            return statuses;
        }

        Set<Long> orderTestIds = new HashSet<>();
        Set<Long> catalogTestIds = new HashSet<>();
        Long encounterId = null;

        for (StatusLookup lookup : diagnosticLookups) {
            if (lookup.encounterId() != null) {
                encounterId = lookup.encounterId();
            }
            if (lookup.orderTestId() != null) {
                orderTestIds.add(lookup.orderTestId());
            }
            if (lookup.sourceId() != null) {
                orderTestIds.add(lookup.sourceId());
                catalogTestIds.add(lookup.sourceId());
            }
            if (lookup.diagnosticTestId() != null) {
                catalogTestIds.add(lookup.diagnosticTestId());
            }
        }

        Map<Long, String> processingByOrderTestId = new HashMap<>();
        Map<Long, String> processingByCatalogTestId = new HashMap<>();

        if (!orderTestIds.isEmpty()) {
            for (DiagnosticOrderTest test : diagnosticOrderTestRepository.findAllById(orderTestIds)) {
                putProcessingStatus(processingByOrderTestId, processingByCatalogTestId, test);
            }
        }

        if (encounterId != null && !catalogTestIds.isEmpty()) {
            for (DiagnosticOrderTest test :
                    diagnosticOrderTestRepository.findActiveByEncounterIdAndTestIdIn(
                            encounterId,
                            catalogTestIds
                    )) {
                putProcessingStatus(processingByOrderTestId, processingByCatalogTestId, test);
            }
        }

        if (encounterId != null) {
            for (DiagnosticOrderTest test :
                    diagnosticOrderTestRepository.findActiveByEncounterId(encounterId)) {
                putProcessingStatus(processingByOrderTestId, processingByCatalogTestId, test);
            }
        }

        for (StatusLookup lookup : diagnosticLookups) {
            String processingStatus = null;
            if (lookup.orderTestId() != null) {
                processingStatus = processingByOrderTestId.get(lookup.orderTestId());
            }
            if (processingStatus == null && lookup.sourceId() != null) {
                processingStatus = processingByOrderTestId.get(lookup.sourceId());
            }
            if (processingStatus == null && lookup.diagnosticTestId() != null) {
                processingStatus = processingByCatalogTestId.get(lookup.diagnosticTestId());
            }
            if (processingStatus == null && lookup.sourceId() != null) {
                processingStatus = processingByCatalogTestId.get(lookup.sourceId());
            }
            if (processingStatus != null) {
                statuses.put(key(lookup), processingStatus);
            }
        }

        return statuses;
    }

    private void putProcessingStatus(
            Map<Long, String> processingByOrderTestId,
            Map<Long, String> processingByCatalogTestId,
            DiagnosticOrderTest test
    ) {
        if (test == null || test.getProcessingStatus() == null) {
            return;
        }
        String processingStatus = test.getProcessingStatus().name();
        processingByOrderTestId.putIfAbsent(test.getId(), processingStatus);
        if (test.getTestId() != null) {
            processingByCatalogTestId.putIfAbsent(test.getTestId(), processingStatus);
        }
    }

    private Map<Long, String> loadProcedureStatuses(Set<Long> ids) {
        Map<Long, String> statuses = new HashMap<>();
        if (ids.isEmpty()) {
            return statuses;
        }
        for (PatientProcedure procedure : patientProcedureRepository.findAllById(ids)) {
            if (procedure.getStatus() != null) {
                statuses.put(procedure.getId(), procedure.getStatus().name());
            }
        }
        return statuses;
    }

    private Map<Long, String> loadMedicationStatuses(Set<Long> ids) {
        Map<Long, String> statuses = new HashMap<>();
        if (ids.isEmpty()) {
            return statuses;
        }
        for (PatientPrescriptionMedication medication :
                patientPrescriptionMedicationRepository.findAllById(ids)) {
            if (medication.getStatus() != null) {
                statuses.put(medication.getId(), medication.getStatus().name());
            }
        }
        return statuses;
    }

    private Map<Long, String> loadConsultationStatuses(Set<Long> ids) {
        Map<Long, String> statuses = new HashMap<>();
        if (ids.isEmpty()) {
            return statuses;
        }
        for (Consultation consultation : consultationRepository.findAllById(ids)) {
            if (consultation.getStatus() != null) {
                statuses.put(consultation.getId(), consultation.getStatus().name());
            }
        }
        return statuses;
    }

    private Map<Long, String> loadDentalStatuses(Set<Long> ids) {
        Map<Long, String> statuses = new HashMap<>();
        if (ids.isEmpty()) {
            return statuses;
        }
        for (DentalProcedure dentalProcedure : dentalProcedureRepository.findAllById(ids)) {
            statuses.put(
                    dentalProcedure.getId(),
                    dentalProcedure.isCancelled() ? "CANCELLED" : "RECORDED"
            );
        }
        return statuses;
    }

    private Map<Long, PatientServiceAndProduct> loadPspItems(
            List<EncounterBillingItemSummary> items
    ) {
        Set<Long> pspIds = items.stream()
                .map(EncounterBillingItemSummary::patientServiceProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (pspIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, PatientServiceAndProduct> byId = new HashMap<>();
        for (PatientServiceAndProduct item : patientServiceAndProductRepository.findAllById(pspIds)) {
            byId.put(item.getId(), item);
        }
        return byId;
    }

    private List<StatusLookup> toLookups(List<PatientServiceAndProduct> items) {
        List<StatusLookup> lookups = new ArrayList<>();
        for (PatientServiceAndProduct item : items) {
            lookups.add(toLookup(item));
        }
        return lookups;
    }

    private StatusLookup toLookup(PatientServiceAndProduct item) {
        if (item == null) {
            return new StatusLookup(null, null, null, null, null, null);
        }
        Long orderTestId =
                item.getDiagnosticTestId() != null
                                || isDiagnostic(item.getServiceSource(), item.getBillingItemType())
                        ? item.getSourceId()
                        : null;
        return new StatusLookup(
                item.getServiceSource(),
                item.getBillingItemType(),
                item.getSourceId(),
                item.getDiagnosticTestId(),
                orderTestId,
                item.getEncounterId()
        );
    }

    private StatusLookup toLookup(
            EncounterBillingItemSummary item,
            Long encounterId,
            Map<Long, PatientServiceAndProduct> pspById
    ) {
        if (item == null) {
            return new StatusLookup(null, null, null, null, null, encounterId);
        }

        PatientServiceAndProduct pspItem =
                item.patientServiceProductId() == null
                        ? null
                        : pspById.get(item.patientServiceProductId());
        if (pspItem != null) {
            return toLookup(pspItem);
        }

        BillingItemTypes type = parseBillingItemType(item.billingItemType());
        return new StatusLookup(
                null,
                type,
                item.sourceId(),
                item.sourceId(),
                null,
                encounterId
        );
    }

    private BillingItemTypes parseBillingItemType(String billingItemType) {
        if (billingItemType == null || billingItemType.isBlank()) {
            return null;
        }
        try {
            return BillingItemTypes.valueOf(billingItemType);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isDiagnostic(StatusLookup lookup) {
        return lookup != null
                && (lookup.diagnosticTestId() != null
                        || isDiagnostic(lookup.serviceSource(), lookup.billingItemType()));
    }

    private boolean isDiagnostic(ServiceSource serviceSource, BillingItemTypes billingItemType) {
        if (serviceSource == ServiceSource.LABORATORY || serviceSource == ServiceSource.RADIOLOGY) {
            return true;
        }
        return billingItemType == BillingItemTypes.LABORATORY
                || billingItemType == BillingItemTypes.RADIOLOGY
                || billingItemType == BillingItemTypes.PATHOLOGY;
    }

    private ServiceSource resolvedSource(StatusLookup lookup) {
        if (lookup.serviceSource() != null) {
            if (lookup.serviceSource() == ServiceSource.SERVICE_AND_PRODUCT
                    || lookup.serviceSource() == ServiceSource.ENCOUNTER_DEFAULT_SERVICE) {
                ServiceSource inferred = inferSource(lookup.billingItemType());
                return inferred != null ? inferred : lookup.serviceSource();
            }
            return lookup.serviceSource();
        }
        ServiceSource inferred = inferSource(lookup.billingItemType());
        return inferred != null ? inferred : ServiceSource.SERVICE_AND_PRODUCT;
    }

    private ServiceSource inferSource(BillingItemTypes type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case LABORATORY, PATHOLOGY -> ServiceSource.LABORATORY;
            case RADIOLOGY -> ServiceSource.RADIOLOGY;
            case PROCEDURE -> ServiceSource.PROCEDURE;
            case MEDICATION -> ServiceSource.PRESCRIPTION;
            default -> null;
        };
    }

    private String key(StatusLookup lookup) {
        return resolvedSource(lookup).name()
                + ":"
                + lookup.sourceId()
                + ":"
                + lookup.diagnosticTestId()
                + ":"
                + lookup.orderTestId();
    }

    private record StatusLookup(
            ServiceSource serviceSource,
            BillingItemTypes billingItemType,
            Long sourceId,
            Long diagnosticTestId,
            Long orderTestId,
            Long encounterId
    ) {
    }
}
