package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.DentalChartAgeRuleClient;
import com.dazzle.asklepios.client.setup.dto.DentalActionSetupDTO;
import com.dazzle.asklepios.client.setup.dto.DentalChartAgeRuleDTO;
import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.enumeration.DentalChartStatus;
import com.dazzle.asklepios.domain.enumeration.DentalChartToothInventoryStatus;
import com.dazzle.asklepios.domain.enumeration.DentalChartType;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.ToothNumberingSystem;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.repository.DentalChartRepository;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.ToothActionLogRepository;
import com.dazzle.asklepios.repository.ToothActionRepository;
import com.dazzle.asklepios.repository.ToothCdtRepository;
import com.dazzle.asklepios.repository.ToothServiceRepository;
import com.dazzle.asklepios.service.dto.dentalChart.ChartUpdateDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalChartResponseDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalChartSummaryDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalChartToothDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothActionDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothActionLogDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothCdtDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ToothServiceDTO;
import com.dazzle.asklepios.service.helper.DentalActionHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.ConflictAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class DentalChartService {

    private static final Logger LOG = LoggerFactory.getLogger(DentalChartService.class);
    private static final String ENTITY_NAME = "dentalChart";
    private static final ObjectMapper LABEL_MAPPER = new ObjectMapper();

    /** Encounter statuses that count as a finished visit; cancelled and still-open encounters never qualify. */
    private static final List<TreatmentStatus> ELIGIBLE_SOURCE_STATUSES =
            List.of(TreatmentStatus.COMPLETED, TreatmentStatus.DISCHARGED, TreatmentStatus.CLOSED);

    private final DentalChartRepository dentalChartRepository;
    private final DentalChartToothRepository dentalChartToothRepository;
    private final ToothActionRepository toothActionRepository;
    private final ToothActionLogRepository toothActionLogRepository;
    private final ToothCdtRepository toothCdtRepository;
    private final ToothServiceRepository toothServiceRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final DentalChartAgeRuleClient dentalChartAgeRuleClient;
    private final DentalActionHelper dentalActionHelper;
    private final DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository;
    private final DentalToothProtectionService dentalToothProtectionService;

    public DentalChartService(
            DentalChartRepository dentalChartRepository,
            DentalChartToothRepository dentalChartToothRepository,
            ToothActionRepository toothActionRepository,
            ToothActionLogRepository toothActionLogRepository,
            ToothCdtRepository toothCdtRepository,
            ToothServiceRepository toothServiceRepository,
            PatientEncounterRepository patientEncounterRepository,
            DentalChartAgeRuleClient dentalChartAgeRuleClient,
            DentalActionHelper dentalActionHelper,
            DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository,
            DentalToothProtectionService dentalToothProtectionService
    ) {
        this.dentalChartRepository = dentalChartRepository;
        this.dentalChartToothRepository = dentalChartToothRepository;
        this.toothActionRepository = toothActionRepository;
        this.toothActionLogRepository = toothActionLogRepository;
        this.toothCdtRepository = toothCdtRepository;
        this.toothServiceRepository = toothServiceRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.dentalChartAgeRuleClient = dentalChartAgeRuleClient;
        this.dentalActionHelper = dentalActionHelper;
        this.dentalPlannedTreatmentRepository = dentalPlannedTreatmentRepository;
        this.dentalToothProtectionService = dentalToothProtectionService;
    }

    /**
     * Takes the chart row lock for the chart that owns the tooth, so clinical writes on the tooth and
     * inventory saves on its chart are serialized. Callers must be inside a transaction.
     */
    public void lockChartOfTooth(Long toothId) {
        Long chartId = dentalChartToothRepository.findChartIdById(toothId)
                .orElseThrow(() -> new NotFoundAlertException("Tooth not found", "dentalChartTooth", "tooth.notfound"));
        dentalChartRepository.findByIdForUpdate(chartId);
    }

    /**
     * The chart of an encounter. One is expected; several (legacy data) are never resolved by picking
     * one: a 409 lists them and nothing is changed.
     */
    public Optional<DentalChart> findChartByEncounterId(Long encounterId) {
        List<DentalChart> charts = dentalChartRepository.findAllByEncounter_IdOrderByIdAsc(encounterId);
        if (charts.size() > 1) {
            String ids = charts.stream().map(c -> String.valueOf(c.getId())).collect(Collectors.joining(", "));
            throw new ConflictAlertException(
                    "Several dental charts exist for this encounter (ids " + ids + "). Nothing was changed; have them reviewed.",
                    ENTITY_NAME,
                    "chart.duplicate"
            );
        }
        return charts.stream().findFirst();
    }

    public DentalChartResponseDTO getOrCreateChartForEncounter(Long encounterId) {
        PatientEncounter encounter = getEncounter(encounterId);

        Optional<DentalChart> existing = findChartByEncounterId(encounterId);
        if (existing.isPresent()) {
            return assembleChart(existing.get());
        }

        // First open: serialize creators per encounter on the encounter row, then re-check, so two
        // concurrent first opens produce a single chart (the unique index is only a backstop).
        patientEncounterRepository.findByIdForUpdate(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found with id " + encounterId,
                        ENTITY_NAME,
                        "encounterNotFound"
                ));
        return findChartByEncounterId(encounterId)
                .map(this::assembleChart)
                .orElseGet(() -> createChartForEncounter(encounter));
    }

    @Transactional(readOnly = true)
    public DentalChartResponseDTO getChartById(Long chartId) {
        DentalChart chart = getChart(chartId);
        return assembleChart(chart);
    }

    /**
     * Read the chart already stored for an encounter. Does not create teeth or a chart.
     */
    @Transactional(readOnly = true)
    public Optional<DentalChartResponseDTO> findExistingChartForEncounter(Long encounterId) {
        return findChartByEncounterId(encounterId).map(this::assembleChart);
    }

    public DentalChartResponseDTO updateChart(Long chartId, ChartUpdateDTO dto) {
        DentalChart chart = getChart(chartId);

        chart.setChartDate(dto.chartDate());
        chart.setChartType(dto.chartType());
        chart.setStatus(dto.status());
        chart.setTreatmentPlan(dto.treatmentPlan());

        DentalChart saved;
        try {
            saved = dentalChartRepository.saveAndFlush(chart);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictAlertException(
                    "The dental chart was changed by someone else. Refresh the chart and try again.",
                    ENTITY_NAME,
                    "chart.stale"
            );
        }
        return assembleChart(saved);
    }

    private DentalChartResponseDTO createChartForEncounter(PatientEncounter encounter) {
        // Later visits inherit the inventory and numbering snapshot of the latest eligible chart.
        // A failing lookup propagates: it must never fall back to Setup and change the inventory.
        Optional<DentalChart> previousChart = findEligiblePreviousChart(encounter);
        if (previousChart.isPresent()) {
            return createChartFromPreviousChart(encounter, previousChart.get());
        }

        Patient patient = encounter.getPatient();
        DentalChartAgeRuleDTO rule = resolveActiveSetupRule(patient, encounter);

        DentalChartType chartType = parseChartType(rule.chartType());
        ToothNumberingSystem numberingSystem = parseNumberingSystem(rule.numberingSystem());
        List<String> toothNumbers = normalizeToothInventory(rule.toothNumbers());
        if (toothNumbers.isEmpty()) {
            throw new BadRequestAlertException(
                    "Dental chart setup rule has no teeth selected. Configure teeth in Dental Chart Setup, then retry.",
                    ENTITY_NAME,
                    "setup.emptyTeeth"
            );
        }

        // Snapshot the resolved custom labels (null = legacy enum numbering).
        String snapshotLabels = serializeLabels(rule.numberingLabels());

        DentalChart newChart = DentalChart.builder()
                .patient(patient)
                .encounter(encounter)
                .chartType(chartType)
                .numberingSystem(numberingSystem)
                .ageRuleId(rule.id())
                .numberingMethodId(snapshotLabels != null ? rule.numberingMethodId() : null)
                .numberingLabels(snapshotLabels)
                .status(DentalChartStatus.NEW)
                .chartDate(Instant.now())
                .build();

        newChart = dentalChartRepository.saveAndFlush(newChart);

        List<DentalChartTooth> teeth = new ArrayList<>();
        for (String toothNumber : toothNumbers) {
            teeth.add(
                    DentalChartTooth.builder()
                            .chartId(newChart.getId())
                            .toothNumber(toothNumber)
                            .missing(false)
                            .build()
            );
        }
        dentalChartToothRepository.saveAll(teeth);
        dentalChartToothRepository.flush();

        return assembleChart(newChart);
    }

    /** Latest chart of an earlier, finished, non-cancelled encounter of the same patient. */
    private Optional<DentalChart> findEligiblePreviousChart(PatientEncounter encounter) {
        LocalDate asOfDate = encounter.getEncounterDate() != null
                ? encounter.getEncounterDate()
                : LocalDate.now();
        return dentalChartRepository.findEligiblePreviousCharts(
                encounter.getPatient().getId(),
                encounter.getId(),
                asOfDate,
                ELIGIBLE_SOURCE_STATUSES,
                PageRequest.of(0, 1)
        ).stream().findFirst();
    }

    /**
     * New chart for a later visit: inventory, missing state and the complete numbering snapshot come
     * from the source chart. Only persistent conditions are carried as existing findings; performed
     * actions, action logs and progress notes stay on the source chart (Previous Chart).
     */
    private DentalChartResponseDTO createChartFromPreviousChart(PatientEncounter encounter, DentalChart source) {
        List<DentalChartTooth> sourceTeeth = dentalChartToothRepository.findByChartIdAndInventoryStatus(
                source.getId(),
                DentalChartToothInventoryStatus.ACTIVE
        );
        if (sourceTeeth.isEmpty()) {
            throw new BadRequestAlertException(
                    "The previous dental chart has no active teeth to carry forward. Review it before opening this visit.",
                    ENTITY_NAME,
                    "inventory.sourceEmpty"
            );
        }
        LOG.info("Creating dental chart from previous chart. sourceChartId={}, encounterId={}", source.getId(), encounter.getId());

        DentalChart newChart = dentalChartRepository.saveAndFlush(
                DentalChart.builder()
                        .patient(encounter.getPatient())
                        .encounter(encounter)
                        .chartType(source.getChartType())
                        .numberingSystem(source.getNumberingSystem())
                        .ageRuleId(source.getAgeRuleId())
                        .numberingMethodId(source.getNumberingMethodId())
                        .numberingLabels(source.getNumberingLabels())
                        .status(DentalChartStatus.NEW)
                        .chartDate(Instant.now())
                        .build()
        );

        Map<String, DentalChartTooth> newByNumber = new HashMap<>();
        for (DentalChartTooth sourceTooth : sourceTeeth) {
            DentalChartTooth created = dentalChartToothRepository.save(
                    DentalChartTooth.builder()
                            .chartId(newChart.getId())
                            .toothNumber(sourceTooth.getToothNumber())
                            .missing(sourceTooth.isMissing())
                            .build()
            );
            newByNumber.put(created.getToothNumber(), created);
        }
        dentalChartToothRepository.flush();

        carryForwardPersistentConditions(sourceTeeth, newByNumber);
        return assembleChart(newChart);
    }

    /**
     * Persistent conditions keep their clinical meaning as existing findings on the same tooth.
     * Actions that are already existing findings, and CONDITION-type actions, qualify; performed
     * treatments do not (they remain on the source chart and are never re-recorded as new work).
     */
    private void carryForwardPersistentConditions(
            List<DentalChartTooth> sourceTeeth,
            Map<String, DentalChartTooth> newByNumber
    ) {
        Map<Long, Boolean> conditionByAction = new HashMap<>();
        for (DentalChartTooth sourceTooth : sourceTeeth) {
            DentalChartTooth target = newByNumber.get(sourceTooth.getToothNumber());
            if (target == null) {
                continue;
            }
            for (ToothAction sourceAction : toothActionRepository.findByToothIdAndCancelledFalse(sourceTooth.getId())) {
                boolean persistent = sourceAction.isExisting() || conditionByAction.computeIfAbsent(
                        sourceAction.getDentalActionId(),
                        id -> {
                            DentalActionSetupDTO action = dentalActionHelper.getDentalAction(id);
                            return action != null && "CONDITION".equalsIgnoreCase(action.type());
                        }
                );
                if (!persistent) {
                    continue;
                }
                toothActionRepository.save(
                        ToothAction.builder()
                                .toothId(target.getId())
                                .dentalActionId(sourceAction.getDentalActionId())
                                .note(sourceAction.getNote())
                                .surface(sourceAction.getSurface())
                                .existing(true)
                                .carriedFromActionId(sourceAction.getCarriedFromActionId() != null
                                        ? sourceAction.getCarriedFromActionId()
                                        : sourceAction.getId())
                                .sourcePlannedTreatmentId(sourceAction.getSourcePlannedTreatmentId())
                                .imageName(sourceAction.getImageName())
                                .toothNumber(sourceAction.getToothNumber())
                                .anesthesiaUsed(sourceAction.getAnesthesiaUsed())
                                .dose(sourceAction.getDose())
                                .cancelled(false)
                                .build()
                );
            }
        }
    }

    private DentalChartAgeRuleDTO resolveActiveSetupRule(Patient patient, PatientEncounter encounter) {
        LocalDate birthDate = patient.getDateOfBirth();
        if (birthDate == null) {
            throw new BadRequestAlertException(
                    "Patient date of birth is required to resolve dental chart setup. Add DOB or configure setup, then retry.",
                    ENTITY_NAME,
                    "setup.unresolved"
            );
        }

        LocalDate asOfDate = encounter.getEncounterDate() != null
                ? encounter.getEncounterDate()
                : LocalDate.now();

        try {
            ResponseEntity<DentalChartAgeRuleDTO> response =
                    dentalChartAgeRuleClient.resolveByBirthDate(birthDate, asOfDate);
            if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BadRequestAlertException(
                        "No active dental chart setup rule matches this patient's age at the encounter date. Configure Dental Chart Setup, then retry.",
                        ENTITY_NAME,
                        "setup.unresolved"
                );
            }
            return response.getBody();
        } catch (BadRequestAlertException ex) {
            throw ex;
        } catch (FeignException.NotFound ex) {
            throw new BadRequestAlertException(
                    "No active dental chart setup rule matches this patient's age at the encounter date. Configure Dental Chart Setup, then retry.",
                    ENTITY_NAME,
                    "setup.unresolved"
            );
        } catch (FeignException ex) {
            LOG.error(
                    "Failed to resolve dental chart age rule birthDate={} asOfDate={} status={}",
                    birthDate,
                    asOfDate,
                    ex.status(),
                    ex
            );
            throw new BadRequestAlertException(
                    "Dental chart setup could not be reached. Check Setup service and retry.",
                    ENTITY_NAME,
                    "setup.unavailable"
            );
        } catch (Exception ex) {
            LOG.error("Unexpected error resolving dental chart age rule", ex);
            throw new BadRequestAlertException(
                    "Dental chart setup could not be resolved. Please retry.",
                    ENTITY_NAME,
                    "setup.unavailable"
            );
        }
    }

    private static DentalChartType parseChartType(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestAlertException(
                    "Dental chart setup rule is missing chart type. Fix the rule in Setup, then retry.",
                    ENTITY_NAME,
                    "setup.unresolved"
            );
        }
        try {
            return DentalChartType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestAlertException(
                    "Dental chart setup rule has an invalid chart type. Fix the rule in Setup, then retry.",
                    ENTITY_NAME,
                    "setup.unresolved"
            );
        }
    }

    private static String serializeLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return null;
        }
        try {
            return LABEL_MAPPER.writeValueAsString(labels);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static Map<String, String> parseLabels(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return LABEL_MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static ToothNumberingSystem parseNumberingSystem(String value) {
        if (value == null || value.isBlank()) {
            return ToothNumberingSystem.UNIVERSAL;
        }
        try {
            return ToothNumberingSystem.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ToothNumberingSystem.UNIVERSAL;
        }
    }

    private static List<String> normalizeToothInventory(List<String> toothNumbers) {
        if (toothNumbers == null || toothNumbers.isEmpty()) {
            return List.of();
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (String raw : toothNumbers) {
            if (raw != null && !raw.isBlank()) {
                ordered.add(raw.trim());
            }
        }
        return List.copyOf(ordered);
    }

    private DentalChartResponseDTO assembleChart(DentalChart chart) {
        List<DentalChartTooth> teeth = dentalChartToothRepository.findByChartId(chart.getId());
        Map<Long, Integer> openTreatments = openTreatmentCounts(teeth);
        Map<Long, String> protection = dentalToothProtectionService.reasonsByToothId(chart, teeth);
        List<DentalChartToothDTO> toothDTOs = new ArrayList<>();
        List<DentalChartToothDTO> inactiveToothDTOs = new ArrayList<>();
        List<ToothActionDTO> chartActions = new ArrayList<>();
        for (DentalChartTooth tooth : teeth) {
            List<ToothAction> actionEntities = toothActionRepository.findByToothIdAndCancelledFalse(tooth.getId());
            Map<Long, ToothAction> origins = new HashMap<>();
            List<Long> originIds = actionEntities.stream()
                    .map(ToothAction::getCarriedFromActionId).filter(Objects::nonNull).distinct().toList();
            if (!originIds.isEmpty()) {
                toothActionRepository.findAllById(originIds).forEach(o -> origins.put(o.getId(), o));
            }
            List<ToothActionDTO> actions = actionEntities.stream()
                    .map(a -> ToothActionDTO.fromEntity(a, origins.get(a.getCarriedFromActionId())))
                    .toList();
            List<ToothActionLogDTO> history = toothActionLogRepository.findByToothIdOrderByLogTimeDesc(tooth.getId())
                    .stream()
                    .map(ToothActionLogDTO::fromEntity)
                    .toList();
            List<ToothServiceDTO> services = toothServiceRepository.findByToothIdAndCancelledFalse(tooth.getId())
                    .stream()
                    .map(ToothServiceDTO::fromEntity)
                    .toList();
            List<ToothCdtDTO> cdts = toothCdtRepository.findByToothIdAndCancelledFalse(tooth.getId())
                    .stream()
                    .map(ToothCdtDTO::fromEntity)
                    .toList();
            DentalChartToothDTO toothDTO = DentalChartToothDTO.fromEntity(
                    tooth, actions, history, services, cdts,
                    openTreatments.getOrDefault(tooth.getId(), 0), protection.get(tooth.getId()));
            // Removed/replaced teeth stay out of the active inventory but keep their history.
            if (tooth.isActiveInventory()) {
                toothDTOs.add(toothDTO);
                chartActions.addAll(actions);
            } else {
                inactiveToothDTOs.add(toothDTO);
            }
        }
        List<DentalChartSummaryDTO> historyCharts = dentalChartRepository
                .findByPatient_IdAndIdNotOrderByCreatedDateDesc(chart.getPatient().getId(), chart.getId())
                .stream()
                .map(DentalChartSummaryDTO::fromEntity)
                .toList();

        return new DentalChartResponseDTO(
                chart.getId(),
                chart.getPatient().getId(),
                chart.getEncounter().getId(),
                chart.getChartDate(),
                chart.getChartType(),
                chart.getStatus(),
                chart.getTreatmentPlan(),
                chart.getNumberingSystem(),
                chart.getAgeRuleId(),
                chart.getNumberingLabels() != null ? chart.getNumberingMethodId() : null,
                parseLabels(chart.getNumberingLabels()),
                chart.getVersion(),
                toothDTOs,
                inactiveToothDTOs,
                chartActions,
                historyCharts
        );
    }

    /** Open planned treatments per tooth id, from one grouped query. */
    private Map<Long, Integer> openTreatmentCounts(List<DentalChartTooth> teeth) {
        if (teeth.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : dentalPlannedTreatmentRepository.countOpenByToothIds(
                teeth.stream().map(DentalChartTooth::getId).toList(), PlannedTreatmentStatus.COMPLETED)) {
            counts.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    private PatientEncounter getEncounter(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found with id " + encounterId,
                        ENTITY_NAME,
                        "encounterNotFound"
                ));
    }

    private DentalChart getChart(Long chartId) {
        return dentalChartRepository.findById(chartId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Dental chart not found: " + chartId,
                        ENTITY_NAME,
                        "notfound"
                ));
    }
}
