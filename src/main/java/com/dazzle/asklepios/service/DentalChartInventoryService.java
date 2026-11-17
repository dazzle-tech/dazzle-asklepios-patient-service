package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.DentalChartInventoryAudit;
import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.DentalChartInventoryAction;
import com.dazzle.asklepios.domain.enumeration.DentalChartToothInventoryStatus;
import com.dazzle.asklepios.domain.enumeration.DentalChartType;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.repository.DentalChartInventoryAuditRepository;
import com.dazzle.asklepios.repository.DentalChartRepository;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.dentalChart.ChartInventoryAuditDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ChartInventoryChangeDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ChartInventoryResultDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ChartInventoryUpdateDTO;
import com.dazzle.asklepios.service.dto.dentalChart.ChartInventoryWarningDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.ConflictAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Edits the tooth inventory of a visit chart. A saved batch is atomic with its audit rows and is
 * protected by the chart version. Teeth are never deleted (they may carry actions, treatments or
 * plans): removed and replaced teeth stay as non-ACTIVE rows. This never records an extraction or
 * shedding and never moves actions, plans or billing between teeth.
 */
@Service
@Transactional
public class DentalChartInventoryService {

    private static final Logger LOG = LoggerFactory.getLogger(DentalChartInventoryService.class);
    private static final String ENTITY_NAME = "dentalChartInventory";

    static final int MAX_TEETH_PER_ARCH = 16;
    static final int MAX_TEETH_TOTAL = 32;
    private static final int MAX_REASON_LENGTH = 500;

    /** Encounters in these states are not editable; an open amendment returns the encounter to ONGOING. */
    private static final Set<TreatmentStatus> LOCKED_ENCOUNTER_STATUSES = Set.of(
            TreatmentStatus.COMPLETED,
            TreatmentStatus.DISCHARGED,
            TreatmentStatus.CLOSED,
            TreatmentStatus.CANCELLED
    );

    private static final Pattern PERMANENT_ID = Pattern.compile("^Tooth([1-9]|[12]\\d|3[0-2])$");
    private static final Pattern PRIMARY_ID = Pattern.compile("^Tooth[A-T]$");

    private final DentalChartRepository dentalChartRepository;
    private final DentalChartToothRepository dentalChartToothRepository;
    private final DentalChartInventoryAuditRepository auditRepository;
    private final DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository;
    private final EncounterReopenGuard encounterReopenGuard;
    private final DentalChartService dentalChartService;
    private final DentalToothProtectionService dentalToothProtectionService;

    public DentalChartInventoryService(
            DentalChartRepository dentalChartRepository,
            DentalChartToothRepository dentalChartToothRepository,
            DentalChartInventoryAuditRepository auditRepository,
            DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository,
            EncounterReopenGuard encounterReopenGuard,
            DentalChartService dentalChartService,
            DentalToothProtectionService dentalToothProtectionService
    ) {
        this.dentalChartRepository = dentalChartRepository;
        this.dentalChartToothRepository = dentalChartToothRepository;
        this.auditRepository = auditRepository;
        this.dentalPlannedTreatmentRepository = dentalPlannedTreatmentRepository;
        this.encounterReopenGuard = encounterReopenGuard;
        this.dentalChartService = dentalChartService;
        this.dentalToothProtectionService = dentalToothProtectionService;
    }

    public ChartInventoryResultDTO updateInventory(Long chartId, ChartInventoryUpdateDTO dto) {
        requireStaff();
        // Chart row lock: clinical writes on this chart wait, so the protection re-check below is race-free.
        DentalChart chart = dentalChartRepository.findByIdForUpdate(chartId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Dental chart not found: " + chartId, ENTITY_NAME, "notfound"));

        String reason = validateRequest(chart, dto);
        PatientEncounter encounter = chart.getEncounter();

        // Stale-save protection: succeeds only for the version the client read; also bumps it.
        if (dentalChartRepository.bumpVersionIfCurrent(chartId, dto.expectedVersion()) == 0) {
            throw new ConflictAlertException(
                    "The dental chart was changed by someone else. Reload the chart and review before saving again.",
                    ENTITY_NAME,
                    "inventory.stale"
            );
        }
        // The bulk update cleared the persistence context; reload the chart with its new version.
        DentalChart current = dentalChartRepository.findById(chartId).orElseThrow();

        Map<String, DentalChartTooth> rows = new HashMap<>();
        for (DentalChartTooth tooth : dentalChartToothRepository.findByChartId(chartId)) {
            rows.put(tooth.getToothNumber(), tooth);
        }
        Set<String> active = rows.values().stream()
                .filter(DentalChartTooth::isActiveInventory)
                .map(DentalChartTooth::getToothNumber)
                .collect(Collectors.toCollection(HashSet::new));

        String batchId = UUID.randomUUID().toString();
        Long reopenSessionId = encounterReopenGuard.findOpenReopenSessionId(encounter.getId()).orElse(null);
        List<DentalChartInventoryAudit> audits = new ArrayList<>();
        List<DentalChartTooth> toSave = new ArrayList<>();
        List<String> deactivatedNumbers = new ArrayList<>();
        Set<String> touched = new HashSet<>();

        for (ChartInventoryChangeDTO change : dto.changes()) {
            String action = change.action() == null ? "" : change.action().trim().toUpperCase(Locale.ROOT);
            String tooth = requireToothId(change.toothNumber());
            if (!touched.add(tooth)) {
                throw new BadRequestAlertException(
                        "Tooth " + tooth + " appears more than once in this change.", ENTITY_NAME, "inventory.duplicateChange");
            }
            switch (action) {
                case "ADD" -> {
                    requireDentitionAllowed(current, tooth);
                    activate(current, chartId, tooth, null, DentalChartInventoryAction.ADD, rows, active, toSave, audits, reason, batchId, reopenSessionId);
                }
                case "REMOVE" -> deactivate(current, tooth, null, DentalChartToothInventoryStatus.REMOVED,
                        DentalChartInventoryAction.REMOVE,
                        rows, active, toSave, audits, deactivatedNumbers, reason, batchId, reopenSessionId);
                case "REPLACE" -> {
                    String replacement = requireToothId(change.replacementToothNumber());
                    if (!touched.add(replacement)) {
                        throw new BadRequestAlertException(
                                "Tooth " + replacement + " appears more than once in this change.",
                                ENTITY_NAME,
                                "inventory.duplicateChange");
                    }
                    if (!isCounterpart(tooth, replacement)) {
                        throw new BadRequestAlertException(
                                "A tooth can only be replaced by the primary/permanent tooth of the same position.",
                                ENTITY_NAME,
                                "inventory.notCounterpart");
                    }
                    requireDentitionAllowed(current, replacement);
                    deactivate(current, tooth, replacement, DentalChartToothInventoryStatus.REPLACED,
                            DentalChartInventoryAction.REPLACE_OUT,
                            rows, active, toSave, audits, deactivatedNumbers, reason, batchId, reopenSessionId);
                    activate(current, chartId, replacement, tooth, DentalChartInventoryAction.REPLACE_IN,
                            rows, active, toSave, audits, reason, batchId, reopenSessionId);
                }
                default -> throw new BadRequestAlertException(
                        "Unknown inventory action: " + change.action(), ENTITY_NAME, "inventory.unknownAction");
            }
        }

        rejectProtectedTeeth(current, rows, deactivatedNumbers);
        validateInventory(active);

        promoteToMixedIfNeeded(current, active, audits, reason, batchId, reopenSessionId);

        dentalChartToothRepository.saveAll(toSave);
        dentalChartToothRepository.flush();
        auditRepository.saveAll(audits);
        dentalChartRepository.flush();

        List<ChartInventoryWarningDTO> warnings = openTreatmentWarnings(rows, deactivatedNumbers);
        LOG.info("Dental chart inventory saved. chartId={} batchId={} changes={}", chartId, batchId, dto.changes().size());
        return new ChartInventoryResultDTO(dentalChartService.getChartById(chartId), warnings);
    }

    @Transactional(readOnly = true)
    public List<ChartInventoryAuditDTO> getInventoryHistory(Long chartId) {
        requireStaff();
        if (!dentalChartRepository.existsById(chartId)) {
            throw new NotFoundAlertException("Dental chart not found: " + chartId, ENTITY_NAME, "notfound");
        }
        return auditRepository.findByChartIdOrderByCreatedDateDescIdDesc(chartId).stream()
                .map(ChartInventoryAuditDTO::fromEntity)
                .toList();
    }

    /** Staff only: patient-portal logins may not read or change the tooth inventory. */
    private static void requireStaff() {
        if (SecurityUtils.isPatientPortalUser()) {
            throw new AccessDeniedException("Patient logins cannot access the dental chart inventory.");
        }
    }

    /** Checks encounter/patient ownership, encounter state and the reason; returns the trimmed reason. */
    private String validateRequest(DentalChart chart, ChartInventoryUpdateDTO dto) {
        PatientEncounter encounter = chart.getEncounter();
        if (!Objects.equals(dto.encounterId(), encounter.getId())) {
            throw new BadRequestAlertException(
                    "This chart does not belong to the given encounter.", ENTITY_NAME, "inventory.encounterMismatch");
        }
        if (!Objects.equals(encounter.getPatient().getId(), chart.getPatient().getId())) {
            throw new BadRequestAlertException(
                    "The chart patient does not match the encounter patient.", ENTITY_NAME, "inventory.patientMismatch");
        }
        if (encounter.getStatus() == null || LOCKED_ENCOUNTER_STATUSES.contains(encounter.getStatus())) {
            throw new BadRequestAlertException(
                    "The tooth inventory cannot be changed while the encounter is completed or cancelled. "
                            + "Start an amendment to change a completed encounter.",
                    ENTITY_NAME,
                    "inventory.encounterNotEditable"
            );
        }
        String reason = dto.reason() == null ? "" : dto.reason().trim();
        if (reason.isEmpty()) {
            throw new BadRequestAlertException("A reason is required.", ENTITY_NAME, "inventory.reasonRequired");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new BadRequestAlertException(
                    "The reason must be at most " + MAX_REASON_LENGTH + " characters.",
                    ENTITY_NAME,
                    "inventory.reasonTooLong"
            );
        }
        return reason;
    }

    /** Teeth with recorded work cannot leave the inventory; one such tooth rejects the whole batch. */
    private void rejectProtectedTeeth(DentalChart chart, Map<String, DentalChartTooth> rows, List<String> deactivatedNumbers) {
        if (deactivatedNumbers.isEmpty()) {
            return;
        }
        dentalToothProtectionService.requireResolvedCoverage(chart);
        List<DentalChartTooth> leaving = deactivatedNumbers.stream().map(rows::get).toList();
        Map<Long, String> reasons = dentalToothProtectionService.reasonsByToothId(chart, leaving);
        if (!reasons.isEmpty()) {
            String numbers = leaving.stream()
                    .filter(t -> reasons.containsKey(t.getId()))
                    .map(DentalChartTooth::getToothNumber)
                    .collect(Collectors.joining(", "));
            throw new BadRequestAlertException(
                    "Teeth with recorded work cannot be removed or replaced: " + numbers + ". No changes were saved.",
                    ENTITY_NAME,
                    "inventory.toothProtected"
            );
        }
    }

    /** Both dentitions present on an Adult/Child chart makes it Mixed (never downgraded). */
    private void promoteToMixedIfNeeded(
            DentalChart chart, Set<String> active, List<DentalChartInventoryAudit> audits,
            String reason, String batchId, Long reopenSessionId
    ) {
        boolean hasPrimary = active.stream().anyMatch(id -> PRIMARY_ID.matcher(id).matches());
        boolean hasPermanent = active.stream().anyMatch(id -> PERMANENT_ID.matcher(id).matches());
        if (hasPrimary && hasPermanent && chart.getChartType() != DentalChartType.MIXED) {
            audits.add(audit(chart, batchId, DentalChartInventoryAction.CHART_TYPE, "CHART", null,
                    chart.getChartType().name(), DentalChartType.MIXED.name(), reason, reopenSessionId));
            chart.setChartType(DentalChartType.MIXED);
            dentalChartRepository.save(chart);
        }
    }

    // ---- change application -------------------------------------------------------------------

    /** Adds the tooth, reusing its existing row (one row per chart and tooth) when it was removed before. */
    private void activate(
            DentalChart chart, Long chartId, String tooth, String relatedTooth, DentalChartInventoryAction action,
            Map<String, DentalChartTooth> rows, Set<String> active, List<DentalChartTooth> toSave,
            List<DentalChartInventoryAudit> audits, String reason, String batchId, Long reopenSessionId
    ) {
        if (active.contains(tooth)) {
            throw new BadRequestAlertException(
                    "Tooth " + tooth + " is already in the inventory.", ENTITY_NAME, "inventory.duplicateTooth");
        }
        DentalChartTooth row = rows.get(tooth);
        String before = null;
        if (row == null) {
            row = DentalChartTooth.builder().chartId(chartId).toothNumber(tooth).missing(false).build();
            rows.put(tooth, row);
        } else {
            before = row.getInventoryStatus().name();
            row.setInventoryStatus(DentalChartToothInventoryStatus.ACTIVE);
        }
        active.add(tooth);
        toSave.add(row);
        audits.add(audit(chart, batchId, action, tooth, relatedTooth, before,
                DentalChartToothInventoryStatus.ACTIVE.name(), reason, reopenSessionId));
    }

    private void deactivate(
            DentalChart chart, String tooth, String relatedTooth, DentalChartToothInventoryStatus newStatus,
            DentalChartInventoryAction action,
            Map<String, DentalChartTooth> rows, Set<String> active, List<DentalChartTooth> toSave,
            List<DentalChartInventoryAudit> audits, List<String> deactivated,
            String reason, String batchId, Long reopenSessionId
    ) {
        DentalChartTooth row = rows.get(tooth);
        if (row == null || !active.contains(tooth)) {
            throw new BadRequestAlertException(
                    "Tooth " + tooth + " is not in the inventory.", ENTITY_NAME, "inventory.toothNotActive");
        }
        String before = row.getInventoryStatus().name();
        row.setInventoryStatus(newStatus);
        active.remove(tooth);
        toSave.add(row);
        deactivated.add(tooth);
        audits.add(audit(chart, batchId, action, tooth, relatedTooth, before, newStatus.name(), reason, reopenSessionId));
    }

    private DentalChartInventoryAudit audit(
            DentalChart chart, String batchId, DentalChartInventoryAction action, String tooth, String relatedTooth,
            String before, String after, String reason, Long reopenSessionId
    ) {
        return DentalChartInventoryAudit.builder()
                .chartId(chart.getId())
                .encounterId(chart.getEncounter().getId())
                .patientId(chart.getPatient().getId())
                .batchId(batchId)
                .action(action)
                .toothNumber(tooth)
                .relatedToothNumber(relatedTooth)
                .statusBefore(before)
                .statusAfter(after)
                .reason(reason)
                .reopenSessionId(reopenSessionId)
                .build();
    }

    // ---- validation ---------------------------------------------------------------------------

    private static String requireToothId(String raw) {
        String id = raw == null ? "" : raw.trim();
        if (!PERMANENT_ID.matcher(id).matches() && !PRIMARY_ID.matcher(id).matches()) {
            throw new BadRequestAlertException(
                    "Unknown tooth identifier: " + id, ENTITY_NAME, "inventory.unknownTooth");
        }
        return id;
    }

    private static void requireDentitionAllowed(DentalChart chart, String tooth) {
        boolean primary = PRIMARY_ID.matcher(tooth).matches();
        if (primary && chart.getChartType() == DentalChartType.ADULT) {
            throw new BadRequestAlertException(
                    "Primary teeth cannot be added to an Adult chart.", ENTITY_NAME, "inventory.dentitionNotAllowed");
        }
    }

    /** Limits and positions of the resulting inventory: 16 per arch, 32 in total, no primary beside its successor. */
    private static void validateInventory(Set<String> active) {
        if (active.isEmpty()) {
            throw new BadRequestAlertException(
                    "The chart must keep at least one tooth.", ENTITY_NAME, "inventory.empty");
        }
        int upper = 0;
        int lower = 0;
        for (String id : active) {
            if (isUpperArch(id)) {
                upper++;
            } else {
                lower++;
            }
            String counterpart = counterpartOf(id);
            if (counterpart != null && active.contains(counterpart)) {
                throw new BadRequestAlertException(
                        "Teeth " + id + " and " + counterpart + " occupy the same position.",
                        ENTITY_NAME,
                        "inventory.positionConflict");
            }
        }
        if (upper > MAX_TEETH_PER_ARCH || lower > MAX_TEETH_PER_ARCH || upper + lower > MAX_TEETH_TOTAL) {
            throw new BadRequestAlertException(
                    "A chart holds at most " + MAX_TEETH_PER_ARCH + " teeth per arch and " + MAX_TEETH_TOTAL + " in total.",
                    ENTITY_NAME,
                    "inventory.limitExceeded");
        }
    }

    private static boolean isUpperArch(String id) {
        if (PRIMARY_ID.matcher(id).matches()) {
            return id.charAt(5) <= 'J';
        }
        return Integer.parseInt(id.substring(5)) <= 16;
    }

    private static boolean isCounterpart(String a, String b) {
        return b.equals(counterpartOf(a));
    }

    /**
     * Primary ToothA..ToothJ sit at upper permanent Tooth4..Tooth13; ToothK..ToothT at lower
     * Tooth20..Tooth29. Returns the tooth of the other dentition at the same position, or null.
     */
    static String counterpartOf(String id) {
        if (PRIMARY_ID.matcher(id).matches()) {
            int index = id.charAt(5) - 'A';
            return "Tooth" + (index <= 9 ? 4 + index : 20 + (index - 10));
        }
        int number = Integer.parseInt(id.substring(5));
        if (number >= 4 && number <= 13) {
            return "Tooth" + (char) ('A' + (number - 4));
        }
        if (number >= 20 && number <= 29) {
            return "Tooth" + (char) ('K' + (number - 20));
        }
        return null;
    }

    private List<ChartInventoryWarningDTO> openTreatmentWarnings(
            Map<String, DentalChartTooth> rows,
            List<String> deactivatedNumbers
    ) {
        if (deactivatedNumbers.isEmpty()) {
            return List.of();
        }
        Map<Long, String> numberById = new HashMap<>();
        for (String number : deactivatedNumbers) {
            numberById.put(rows.get(number).getId(), number);
        }
        List<ChartInventoryWarningDTO> warnings = new ArrayList<>();
        for (Object[] row : dentalPlannedTreatmentRepository.countOpenByToothIds(numberById.keySet(), PlannedTreatmentStatus.COMPLETED)) {
            warnings.add(new ChartInventoryWarningDTO(numberById.get((Long) row[0]), ((Number) row[1]).intValue()));
        }
        return warnings;
    }
}
