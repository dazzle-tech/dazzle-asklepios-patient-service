package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ConsultationLog;
import com.dazzle.asklepios.domain.EncounterAmendmentChangeLog;
import com.dazzle.asklepios.domain.enumeration.AmendmentHistoryAction;
import com.dazzle.asklepios.domain.enumeration.AmendmentMedicalSheet;
import com.dazzle.asklepios.domain.EncounterAssessmentLog;
import com.dazzle.asklepios.domain.EncounterPlanFieldAudit;
import com.dazzle.asklepios.domain.PatientEncounterFieldAudit;
import com.dazzle.asklepios.domain.ProgressNoteLog;
import com.dazzle.asklepios.domain.VitalSignsLog;
import com.dazzle.asklepios.web.rest.vm.patientEncounter.AmendmentHistoryChangeVM;
import com.dazzle.asklepios.web.rest.vm.patientEncounter.AmendmentHistoryFieldChangeVM;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AmendmentHistoryNormalizer {

    private static final Logger LOG = LoggerFactory.getLogger(AmendmentHistoryNormalizer.class);

    static final String PROGRESS_NOTES = "Progress Notes";
    static final String VITAL_SIGNS = "Vital Signs";
    static final String CONSULTATION = "Consultation";
    static final String ENCOUNTER_NARRATIVE = "Encounter Narrative";
    static final String ASSESSMENT = "Assessment";
    static final String PLAN = "Plan";

    private static final Set<AmendmentMedicalSheet> REPLACEMENT_SHEETS = EnumSet.of(
            AmendmentMedicalSheet.BODY_MEASUREMENTS,
            AmendmentMedicalSheet.PAIN_ASSESSMENT,
            AmendmentMedicalSheet.ADDITIONAL_MEASUREMENTS
    );

    /**
     * Observations / Complaints keeps every row and inserts the next full snapshot.
     * That is versioning of one encounter sheet, not the deactivate-and-insert lifecycle.
     */
    private static final Set<AmendmentMedicalSheet> SNAPSHOT_VERSION_SHEETS = EnumSet.of(
            AmendmentMedicalSheet.OBSERVATIONS_COMPLAINTS
    );

    private static final Set<String> UNIFIED_LIFECYCLE_KEYS = Set.of(
            "id",
            "recordId",
            "isActive",
            "is_active",
            "patient",
            "patientId",
            "patient_id",
            "encounter",
            "encounterId",
            "encounter_id",
            "reopenSessionId",
            "reopen_session_id",
            "createdBy",
            "createdDate",
            "created_by",
            "created_date",
            "lastModifiedBy",
            "lastModifiedDate",
            "last_modified_by",
            "last_modified_date",
            "version"
    );

    private static final Set<String> VITAL_SIGNS_REPLACEMENT_KEYS = Set.of(
            "is_active",
            "isActive",
            "reopen_session_id"
    );

    private static final Set<String> TECHNICAL_KEYS = Set.of(
            "id",
            "patient_id",
            "encounter_id",
            "created_by",
            "created_date",
            "last_modified_by",
            "last_modified_date",
            "consultation_number"
    );

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("blood_pressure_systolic", "Blood Pressure Systolic"),
            Map.entry("blood_pressure_diastolic", "Blood Pressure Diastolic"),
            Map.entry("heart_rate", "Heart Rate"),
            Map.entry("temperature", "Temperature"),
            Map.entry("oxygen_saturation", "Oxygen Saturation"),
            Map.entry("respiratory_rate", "Respiratory Rate"),
            Map.entry("fasting_blood_glucose", "Fasting Blood Glucose"),
            Map.entry("measurement_site", "Measurement Site"),
            Map.entry("notes", "Notes"),
            Map.entry("noteText", "Progress Note"),
            Map.entry("is_triage", "Triage"),
            Map.entry("is_active", "Active"),
            Map.entry("consultation_content", "Consultation Content"),
            Map.entry("consultation_type", "Consultation Type"),
            Map.entry("consultation_method", "Consultation Method"),
            Map.entry("consultation_level", "Consultation Level"),
            Map.entry("consultant_speciality", "Consultant Speciality"),
            Map.entry("destination_type", "Destination Type"),
            Map.entry("status", "Status"),
            Map.entry("cancellation_reason", "Cancellation Reason"),
            Map.entry("cancelled_date", "Cancelled At"),
            Map.entry("cancelled_by", "Cancelled By"),
            Map.entry("response_text", "Response"),
            Map.entry("reject_reason", "Reject Reason"),
            Map.entry("approval_number", "Approval Number"),
            Map.entry("extra_document", "Extra Document"),
            Map.entry("from_facility_id", "From Facility"),
            Map.entry("to_facility_id", "To Facility"),
            Map.entry("from_department_id", "From Department"),
            Map.entry("to_department_id", "To Department"),
            Map.entry("practitioner_id", "Practitioner"),
            Map.entry("chiefComplaint", "Chief Complaint"),
            Map.entry("historyOfPresentIllness", "History of Present Illness"),
            Map.entry("physicalExaminationSummery", "Physical Examination Summary"),
            Map.entry("physicalExaminationSummary", "Physical Examination Summary"),
            Map.entry("assessment", "Assessment"),
            Map.entry("goals", "Goals"),
            Map.entry("treatmentPlan", "Treatment Plan")
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AmendmentHistoryChangeVM unified(EncounterAmendmentChangeLog log) {
        JsonNode data = read(log.getChangeData());
        if (data == null || !data.hasNonNull("medicalSheet") || !data.hasNonNull("action")) {
            return null;
        }
        AmendmentHistoryAction action;
        try {
            action = AmendmentHistoryAction.valueOf(data.get("action").asText());
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String code = data.get("medicalSheet").asText();
        String module = code;
        try {
            module = AmendmentMedicalSheet.valueOf(code).label();
        } catch (IllegalArgumentException ignored) {
            module = label(code);
        }
        Map<String, Object> before = objectMap(data.get("before"));
        Map<String, Object> after = objectMap(data.get("after"));
        Long recordId = data.hasNonNull("recordId") && data.get("recordId").canConvertToLong()
                ? data.get("recordId").asLong()
                : null;
        return new AmendmentHistoryChangeVM(
                module,
                code,
                action,
                recordId,
                log.getChangedBy(),
                log.getChangedAt(),
                fieldRows(before, after),
                before,
                after
        );
    }

    /**
     * Body Measurements, Pain Assessment, and Additional Measurements save by
     * deactivating the previous row and inserting a replacement. Those two unified
     * events become one clinical change. The old values come from that record's
     * earlier audit events, including an earlier session. If that baseline cannot
     * be reconstructed, both raw events are kept.
     * <p>
     * Observations / Complaints is separate. Each insert is another snapshot of the
     * same encounter sheet, so a later ADDED is compared with the previous audit
     * snapshot and shown as CHANGED.
     */
    public Map<Long, List<AmendmentHistoryChangeVM>> unifiedHistory(
            List<EncounterAmendmentChangeLog> logs,
            Long encounterId
    ) {
        Map<Long, List<AmendmentHistoryChangeVM>> bySession = new LinkedHashMap<>();
        if (logs == null || logs.isEmpty()) {
            return bySession;
        }
        List<EncounterAmendmentChangeLog> ordered = logs.stream()
                .filter(log -> log.getReopenSessionId() != null)
                .filter(log -> log.getEncounterId() == null || encounterId == null || encounterId.equals(log.getEncounterId()))
                .sorted(Comparator
                        .comparing(EncounterAmendmentChangeLog::getChangedAt, Comparator.nullsLast(Instant::compareTo))
                        .thenComparing(EncounterAmendmentChangeLog::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
        Map<AmendmentMedicalSheet, List<EncounterAmendmentChangeLog>> replacements = new EnumMap<>(AmendmentMedicalSheet.class);
        Map<AmendmentMedicalSheet, List<EncounterAmendmentChangeLog>> snapshots = new EnumMap<>(AmendmentMedicalSheet.class);
        for (EncounterAmendmentChangeLog log : ordered) {
            AmendmentMedicalSheet replacement = replacementSheet(log);
            if (replacement != null) {
                replacements.computeIfAbsent(replacement, ignored -> new ArrayList<>()).add(log);
                continue;
            }
            AmendmentMedicalSheet snapshot = snapshotSheet(log);
            if (snapshot != null) {
                snapshots.computeIfAbsent(snapshot, ignored -> new ArrayList<>()).add(log);
                continue;
            }
            addUnified(bySession, log.getReopenSessionId(), unified(log));
        }
        replacements.forEach((sheet, sheetLogs) -> applyReplacements(bySession, sheet, sheetLogs));
        snapshots.forEach((sheet, sheetLogs) -> applySnapshotVersions(bySession, sheet, sheetLogs));
        return bySession;
    }

    public AmendmentHistoryChangeVM progressNote(ProgressNoteLog log) {
        JsonNode payload = read(log.getPayload());
        AmendmentHistoryAction action = progressNoteAction(log.getAction(), payload);
        List<AmendmentHistoryFieldChangeVM> fields = new ArrayList<>();
        fields.add(new AmendmentHistoryFieldChangeVM(
                "Progress Note",
                log.getOldNoteText(),
                log.getNewNoteText()
        ));
        if (action == AmendmentHistoryAction.CANCELLED) {
            addIfPresent(fields, "Cancellation Reason", textAt(payload, "old", "cancellation_reason"), textAt(payload, "new", "cancellation_reason"));
        }
        return change(
                PROGRESS_NOTES,
                "PROGRESS_NOTES",
                action,
                log.getProgressNoteId(),
                firstText(log.getLastModifiedBy(), log.getCreatedBy()),
                log.getCreatedDate(),
                fields
        );
    }

    public AmendmentHistoryChangeVM vitalSigns(VitalSignsLog log, Long encounterId) {
        return jsonChange(
                VITAL_SIGNS,
                log.getAction(),
                log.getVitalSignsId(),
                firstText(log.getLastModifiedBy(), log.getCreatedBy()),
                log.getCreatedDate(),
                log.getPayload(),
                false,
                encounterId
        );
    }

    /**
     * A Vital Signs save deactivates the previous reading and inserts the replacement.
     * Those two audit rows are one clinical change. A lone insert stays ADDED.
     */
    public List<AmendmentHistoryChangeVM> vitalSigns(List<VitalSignsLog> logs, Long encounterId) {
        if (logs == null || logs.isEmpty()) {
            return List.of();
        }
        List<AmendmentHistoryChangeVM> changes = new ArrayList<>();
        VitalSignsLog pendingDeactivation = null;
        for (VitalSignsLog log : logs) {
            if (isPureDeactivation(log, encounterId)) {
                addVitalSigns(changes, pendingDeactivation, encounterId);
                pendingDeactivation = log;
                continue;
            }
            if (pendingDeactivation != null && isFollowingReplacement(pendingDeactivation, log, encounterId)) {
                AmendmentHistoryChangeVM paired = replacementChange(pendingDeactivation, log);
                pendingDeactivation = null;
                if (paired != null) {
                    changes.add(paired);
                }
                continue;
            }
            addVitalSigns(changes, pendingDeactivation, encounterId);
            pendingDeactivation = null;
            addVitalSigns(changes, log, encounterId);
        }
        addVitalSigns(changes, pendingDeactivation, encounterId);
        return changes;
    }

    public AmendmentHistoryChangeVM consultation(ConsultationLog log, Long encounterId) {
        return jsonChange(
                CONSULTATION,
                log.getAction(),
                log.getConsultationId(),
                firstText(log.getLastModifiedBy(), log.getCreatedBy()),
                log.getCreatedDate(),
                log.getPayload(),
                true,
                encounterId
        );
    }

    public AmendmentHistoryChangeVM narrative(PatientEncounterFieldAudit audit, Long encounterId) {
        Long storedEncounterId = audit.getPatientEncounter() == null ? null : audit.getPatientEncounter().getId();
        if (encounterId != null && storedEncounterId != null && !encounterId.equals(storedEncounterId)) {
            return null;
        }
        return fieldChange(
                ENCOUNTER_NARRATIVE,
                audit.getOperationType(),
                audit.getPatientEncounter() == null ? null : audit.getPatientEncounter().getId(),
                audit.getLogBy(),
                audit.getLogDate(),
                label(audit.getFieldName()),
                audit.getOldValue(),
                audit.getNewValue()
        );
    }

    public AmendmentHistoryChangeVM assessment(EncounterAssessmentLog audit) {
        Long recordId = audit.getEncounterAssessment() == null ? null : audit.getEncounterAssessment().getId();
        return fieldChange(
                ASSESSMENT,
                audit.getOperationType(),
                recordId,
                audit.getLogBy(),
                audit.getLogDate(),
                label(audit.getFieldName()),
                audit.getOldValue(),
                audit.getNewValue()
        );
    }

    public AmendmentHistoryChangeVM plan(EncounterPlanFieldAudit audit) {
        return fieldChange(
                PLAN,
                audit.getOperationType(),
                audit.getEncounterPlanId(),
                audit.getLogBy(),
                audit.getLogDate(),
                label(audit.getFieldName()),
                audit.getOldValue(),
                audit.getNewValue()
        );
    }

    /**
     * Each Observations / Complaints ADDED is a full snapshot. When an earlier audit
     * snapshot exists for the same encounter, the history shows only the clinical
     * fields that differ. The baseline may come from an earlier session. A CHANGED
     * delta on that snapshot is applied before the next insert is compared.
     */
    private void applySnapshotVersions(
            Map<Long, List<AmendmentHistoryChangeVM>> bySession,
            AmendmentMedicalSheet sheet,
            List<EncounterAmendmentChangeLog> sheetLogs
    ) {
        Map<Long, ObservationBaseline> baselines = new LinkedHashMap<>();
        for (EncounterAmendmentChangeLog log : sheetLogs) {
            JsonNode data = read(log.getChangeData());
            AmendmentHistoryAction action = actionOf(data);
            Long encounter = log.getEncounterId();
            ObservationBaseline baseline = encounter == null ? null : baselines.get(encounter);
            if (action == AmendmentHistoryAction.ADDED) {
                if (baseline != null && baseline.reliable()) {
                    List<AmendmentHistoryFieldChangeVM> fields = diff(
                            objectMapper.valueToTree(baseline.values()),
                            data == null ? null : data.get("after"),
                            UNIFIED_LIFECYCLE_KEYS
                    );
                    if (!fields.isEmpty()) {
                        addUnified(bySession, log.getReopenSessionId(), change(
                                sheet.label(),
                                sheet.name(),
                                AmendmentHistoryAction.CHANGED,
                                recordId(log),
                                log.getChangedBy(),
                                log.getChangedAt(),
                                fields
                        ));
                    }
                } else {
                    addUnified(bySession, log.getReopenSessionId(), snapshotAdded(log, sheet, data));
                }
                rememberSnapshot(baselines, encounter, recordId(log), data == null ? null : data.get("after"));
            } else if (action == AmendmentHistoryAction.CHANGED) {
                addUnified(bySession, log.getReopenSessionId(), snapshotClinical(log, sheet, data, action));
                if (encounter == null) {
                    continue;
                }
                if (baseline != null && baseline.reliable() && java.util.Objects.equals(baseline.recordId(), recordId(log))) {
                    applyDelta(baseline.values(), objectMap(data.get("before")), objectMap(data.get("after")));
                } else if (baseline == null || !baseline.reliable()) {
                    baselines.put(encounter, ObservationBaseline.unreliable());
                }
            } else if (action == AmendmentHistoryAction.REMOVED || action == AmendmentHistoryAction.CANCELLED) {
                addUnified(bySession, log.getReopenSessionId(), snapshotClinical(log, sheet, data, action));
                if (encounter != null && baseline != null && java.util.Objects.equals(baseline.recordId(), recordId(log))) {
                    baselines.put(encounter, ObservationBaseline.unreliable());
                }
            } else {
                addUnified(bySession, log.getReopenSessionId(), unified(log));
            }
        }
    }

    private void rememberSnapshot(
            Map<Long, ObservationBaseline> baselines,
            Long encounterId,
            Long recordId,
            JsonNode after
    ) {
        if (encounterId == null) {
            return;
        }
        Map<String, Object> snapshot = objectMap(after);
        if (snapshot == null) {
            baselines.put(encounterId, ObservationBaseline.unreliable());
            return;
        }
        baselines.put(encounterId, ObservationBaseline.of(recordId, new LinkedHashMap<>(snapshot)));
    }

    private AmendmentHistoryChangeVM snapshotAdded(
            EncounterAmendmentChangeLog log,
            AmendmentMedicalSheet sheet,
            JsonNode data
    ) {
        Map<String, Object> after = objectMap(data == null ? null : data.get("after"));
        List<AmendmentHistoryFieldChangeVM> fields = new ArrayList<>();
        if (after != null) {
            for (Map.Entry<String, Object> entry : after.entrySet()) {
                if (UNIFIED_LIFECYCLE_KEYS.contains(entry.getKey()) || TECHNICAL_KEYS.contains(entry.getKey())) {
                    continue;
                }
                String value = entry.getValue() == null ? null : String.valueOf(entry.getValue());
                if (blank(value)) {
                    continue;
                }
                fields.add(new AmendmentHistoryFieldChangeVM(label(entry.getKey()), null, value));
            }
        }
        return change(
                sheet.label(),
                sheet.name(),
                AmendmentHistoryAction.ADDED,
                recordId(log),
                log.getChangedBy(),
                log.getChangedAt(),
                fields
        );
    }

    private AmendmentHistoryChangeVM snapshotClinical(
            EncounterAmendmentChangeLog log,
            AmendmentMedicalSheet sheet,
            JsonNode data,
            AmendmentHistoryAction action
    ) {
        List<AmendmentHistoryFieldChangeVM> fields = diff(
                data == null ? null : data.get("before"),
                data == null ? null : data.get("after"),
                UNIFIED_LIFECYCLE_KEYS
        );
        if (fields.isEmpty()) {
            return null;
        }
        return change(
                sheet.label(),
                sheet.name(),
                action,
                recordId(log),
                log.getChangedBy(),
                log.getChangedAt(),
                fields
        );
    }

    private record ObservationBaseline(Long recordId, Map<String, Object> values, boolean reliable) {
        private static ObservationBaseline unreliable() {
            return new ObservationBaseline(null, null, false);
        }

        private static ObservationBaseline of(Long recordId, Map<String, Object> values) {
            return new ObservationBaseline(recordId, values, true);
        }
    }

    private void applyReplacements(
            Map<Long, List<AmendmentHistoryChangeVM>> bySession,
            AmendmentMedicalSheet sheet,
            List<EncounterAmendmentChangeLog> sheetLogs
    ) {
        EncounterAmendmentChangeLog pending = null;
        for (EncounterAmendmentChangeLog log : sheetLogs) {
            if (isUnifiedDeactivation(log, sheet)) {
                addUnified(bySession, pending == null ? null : pending.getReopenSessionId(), pending == null ? null : deactivationPresentation(pending));
                pending = log;
                continue;
            }
            if (pending != null && isUnifiedReplacement(pending, log, sheet)) {
                ReplacementResult paired = unifiedReplacement(sheet, sheetLogs, pending, log);
                if (!paired.resolved()) {
                    addUnified(bySession, pending.getReopenSessionId(), deactivationPresentation(pending));
                    addUnified(bySession, log.getReopenSessionId(), unified(log));
                } else {
                    addUnified(bySession, log.getReopenSessionId(), paired.change());
                }
                pending = null;
                continue;
            }
            addUnified(bySession, pending == null ? null : pending.getReopenSessionId(), pending == null ? null : deactivationPresentation(pending));
            pending = null;
            addUnified(bySession, log.getReopenSessionId(), unified(log));
        }
        addUnified(bySession, pending == null ? null : pending.getReopenSessionId(), pending == null ? null : deactivationPresentation(pending));
    }

    private ReplacementResult unifiedReplacement(
            AmendmentMedicalSheet sheet,
            List<EncounterAmendmentChangeLog> sheetLogs,
            EncounterAmendmentChangeLog deactivation,
            EncounterAmendmentChangeLog replacement
    ) {
        Map<String, Object> baseline = deactivationSnapshot(deactivation);
        if (baseline == null) {
            baseline = baselineBefore(sheet, sheetLogs, deactivation);
        }
        if (baseline == null) {
            return ReplacementResult.unresolved();
        }
        JsonNode inserted = read(replacement.getChangeData());
        JsonNode after = inserted == null ? null : inserted.get("after");
        List<AmendmentHistoryFieldChangeVM> fields = diff(
                objectMapper.valueToTree(baseline),
                after,
                UNIFIED_LIFECYCLE_KEYS
        );
        if (fields.isEmpty()) {
            return ReplacementResult.consumed();
        }
        return ReplacementResult.of(change(
                sheet.label(),
                sheet.name(),
                AmendmentHistoryAction.CHANGED,
                recordId(replacement),
                replacement.getChangedBy(),
                replacement.getChangedAt(),
                fields
        ));
    }

    private record ReplacementResult(boolean resolved, AmendmentHistoryChangeVM change) {
        private static ReplacementResult unresolved() {
            return new ReplacementResult(false, null);
        }

        private static ReplacementResult consumed() {
            return new ReplacementResult(true, null);
        }

        private static ReplacementResult of(AmendmentHistoryChangeVM change) {
            return new ReplacementResult(true, change);
        }
    }

    private Map<String, Object> baselineBefore(
            AmendmentMedicalSheet sheet,
            List<EncounterAmendmentChangeLog> sheetLogs,
            EncounterAmendmentChangeLog deactivation
    ) {
        Long recordId = recordId(deactivation);
        if (recordId == null) {
            return null;
        }
        Map<String, Object> state = null;
        boolean sawAdded = false;
        for (EncounterAmendmentChangeLog log : sheetLogs) {
            if (!isBefore(log, deactivation) || !recordId.equals(recordId(log)) || replacementSheet(log) != sheet) {
                continue;
            }
            JsonNode data = read(log.getChangeData());
            AmendmentHistoryAction action = actionOf(data);
            if (action == AmendmentHistoryAction.ADDED) {
                Map<String, Object> snapshot = objectMap(data == null ? null : data.get("after"));
                state = snapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(snapshot);
                sawAdded = true;
            } else if (action == AmendmentHistoryAction.CHANGED) {
                if (!sawAdded) {
                    return null;
                }
                applyDelta(state, objectMap(data.get("before")), objectMap(data.get("after")));
            } else if (action == AmendmentHistoryAction.REMOVED || action == AmendmentHistoryAction.CANCELLED) {
                return null;
            }
        }
        return sawAdded ? state : null;
    }

    /**
     * A deactivation written with the clinical snapshot carries the values that
     * were current when the row was retired. Older rows stored only isActive.
     */
    private Map<String, Object> deactivationSnapshot(EncounterAmendmentChangeLog deactivation) {
        JsonNode data = read(deactivation.getChangeData());
        Map<String, Object> before = objectMap(data == null ? null : data.get("before"));
        if (before == null) {
            return null;
        }
        boolean clinical = before.keySet().stream().anyMatch(key -> !UNIFIED_LIFECYCLE_KEYS.contains(key));
        if (!clinical) {
            return null;
        }
        return new LinkedHashMap<>(before);
    }

    private AmendmentHistoryChangeVM deactivationPresentation(EncounterAmendmentChangeLog log) {
        JsonNode data = read(log.getChangeData());
        AmendmentMedicalSheet sheet = replacementSheet(log);
        if (sheet == null || data == null) {
            return unified(log);
        }
        List<AmendmentHistoryFieldChangeVM> fields = diff(data.get("before"), data.get("after"), Set.of());
        if (fields.isEmpty()) {
            return null;
        }
        return change(
                sheet.label(),
                sheet.name(),
                AmendmentHistoryAction.CHANGED,
                recordId(log),
                log.getChangedBy(),
                log.getChangedAt(),
                fields
        );
    }

    private static void applyDelta(Map<String, Object> state, Map<String, Object> before, Map<String, Object> after) {
        if (after != null) {
            after.forEach((key, value) -> {
                if (value == null) {
                    state.remove(key);
                } else {
                    state.put(key, value);
                }
            });
        }
        if (before != null) {
            before.keySet().forEach(key -> {
                if (after == null || !after.containsKey(key)) {
                    state.remove(key);
                }
            });
        }
    }

    private boolean isUnifiedDeactivation(EncounterAmendmentChangeLog log, AmendmentMedicalSheet sheet) {
        if (replacementSheet(log) != sheet) {
            return false;
        }
        JsonNode data = read(log.getChangeData());
        if (actionOf(data) != AmendmentHistoryAction.CHANGED) {
            return false;
        }
        JsonNode before = data.get("before");
        JsonNode after = data.get("after");
        return "true".equals(text(activeValue(before)))
                && "false".equals(text(activeValue(after)))
                && diff(before, after, Set.of("isActive", "is_active")).isEmpty();
    }

    private boolean isUnifiedReplacement(
            EncounterAmendmentChangeLog deactivation,
            EncounterAmendmentChangeLog replacement,
            AmendmentMedicalSheet sheet
    ) {
        if (replacementSheet(replacement) != sheet || !isBefore(deactivation, replacement)) {
            return false;
        }
        JsonNode data = read(replacement.getChangeData());
        Long previousId = recordId(deactivation);
        Long nextId = recordId(replacement);
        return actionOf(data) == AmendmentHistoryAction.ADDED
                && deactivation.getReopenSessionId().equals(replacement.getReopenSessionId())
                && sameEncounter(deactivation, replacement)
                && previousId != null
                && nextId != null
                && !previousId.equals(nextId);
    }

    private static JsonNode activeValue(JsonNode node) {
        if (node == null) {
            return null;
        }
        return node.has("isActive") ? node.get("isActive") : node.get("is_active");
    }

    private AmendmentMedicalSheet replacementSheet(EncounterAmendmentChangeLog log) {
        AmendmentMedicalSheet sheet = medicalSheet(log);
        return sheet != null && REPLACEMENT_SHEETS.contains(sheet) ? sheet : null;
    }

    private AmendmentMedicalSheet snapshotSheet(EncounterAmendmentChangeLog log) {
        AmendmentMedicalSheet sheet = medicalSheet(log);
        return sheet != null && SNAPSHOT_VERSION_SHEETS.contains(sheet) ? sheet : null;
    }

    private AmendmentMedicalSheet medicalSheet(EncounterAmendmentChangeLog log) {
        JsonNode data = read(log.getChangeData());
        if (data == null || !data.hasNonNull("medicalSheet")) {
            return null;
        }
        try {
            return AmendmentMedicalSheet.valueOf(data.get("medicalSheet").asText());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private AmendmentHistoryAction actionOf(JsonNode data) {
        if (data == null || !data.hasNonNull("action")) {
            return null;
        }
        try {
            return AmendmentHistoryAction.valueOf(data.get("action").asText());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Long recordId(EncounterAmendmentChangeLog log) {
        JsonNode data = read(log.getChangeData());
        if (data == null || !data.hasNonNull("recordId") || !data.get("recordId").canConvertToLong()) {
            return null;
        }
        return data.get("recordId").asLong();
    }

    private static boolean sameEncounter(EncounterAmendmentChangeLog left, EncounterAmendmentChangeLog right) {
        return left.getEncounterId() != null && left.getEncounterId().equals(right.getEncounterId());
    }

    private static boolean isBefore(EncounterAmendmentChangeLog earlier, EncounterAmendmentChangeLog later) {
        if (earlier.getChangedAt() != null && later.getChangedAt() != null) {
            int byTime = earlier.getChangedAt().compareTo(later.getChangedAt());
            if (byTime > 0) {
                return false;
            }
            if (byTime < 0) {
                return true;
            }
        }
        if (earlier.getId() != null && later.getId() != null) {
            return earlier.getId() < later.getId();
        }
        return false;
    }

    private static void addUnified(
            Map<Long, List<AmendmentHistoryChangeVM>> bySession,
            Long sessionId,
            AmendmentHistoryChangeVM change
    ) {
        if (sessionId == null || change == null) {
            return;
        }
        bySession.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add(change);
    }

    private AmendmentHistoryChangeVM jsonChange(
            String module,
            String rawAction,
            Long recordId,
            String changedBy,
            java.time.Instant changedAt,
            String payloadText,
            boolean cancellationAware,
            Long encounterId
    ) {
        JsonNode payload = read(payloadText);
        if (!belongsToEncounter(payload, encounterId)) {
            return null;
        }
        String operation = rawAction == null ? "" : rawAction.trim().toUpperCase(Locale.ROOT);
        AmendmentHistoryAction action = switch (operation) {
            case "INSERT" -> AmendmentHistoryAction.ADDED;
            case "DELETE" -> AmendmentHistoryAction.REMOVED;
            default -> AmendmentHistoryAction.CHANGED;
        };
        List<AmendmentHistoryFieldChangeVM> fields;
        if (action == AmendmentHistoryAction.CHANGED && payload != null && payload.has("old") && payload.has("new")) {
            if (cancellationAware && becameCancelled(payload)) {
                action = AmendmentHistoryAction.CANCELLED;
            }
            fields = diff(payload.get("old"), payload.get("new"));
        } else if (action == AmendmentHistoryAction.ADDED) {
            fields = snapshot(payload, true);
        } else if (action == AmendmentHistoryAction.REMOVED) {
            fields = snapshot(payload, false);
        } else {
            fields = List.of();
        }
        return change(module, sheetCode(module), action, recordId, changedBy, changedAt, fields);
    }

    private AmendmentHistoryChangeVM change(
            String module,
            String medicalSheet,
            AmendmentHistoryAction action,
            Long recordId,
            String changedBy,
            java.time.Instant changedAt,
            List<AmendmentHistoryFieldChangeVM> fields
    ) {
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (AmendmentHistoryFieldChangeVM field : fields) {
            if (field.oldValue() != null) {
                before.put(field.field(), field.oldValue());
            }
            if (field.newValue() != null) {
                after.put(field.field(), field.newValue());
            }
        }
        return new AmendmentHistoryChangeVM(
                module,
                medicalSheet,
                action,
                recordId,
                changedBy,
                changedAt,
                fields,
                before.isEmpty() ? null : before,
                after.isEmpty() ? null : after
        );
    }

    private List<AmendmentHistoryFieldChangeVM> fieldRows(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> left = before == null ? Map.of() : before;
        Map<String, Object> right = after == null ? Map.of() : after;
        List<AmendmentHistoryFieldChangeVM> rows = new ArrayList<>();
        for (String key : left.keySet()) {
            rows.add(new AmendmentHistoryFieldChangeVM(
                    label(key),
                    left.get(key) == null ? null : String.valueOf(left.get(key)),
                    right.containsKey(key) && right.get(key) != null ? String.valueOf(right.get(key)) : null
            ));
        }
        for (String key : right.keySet()) {
            if (!left.containsKey(key)) {
                rows.add(new AmendmentHistoryFieldChangeVM(
                        label(key),
                        null,
                        right.get(key) == null ? null : String.valueOf(right.get(key))
                ));
            }
        }
        return rows;
    }

    private Map<String, Object> objectMap(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return null;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            values.put(name, jsonValue(node.get(name)));
        }
        return values.isEmpty() ? null : values;
    }

    private static Object jsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.isIntegralNumber() ? node.longValue() : node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }

    private static String sheetCode(String module) {
        return switch (module) {
            case VITAL_SIGNS -> "VITAL_SIGNS";
            case CONSULTATION -> "CONSULTATION";
            case ENCOUNTER_NARRATIVE -> "ENCOUNTER_NARRATIVE";
            case ASSESSMENT -> "ASSESSMENT";
            case PLAN -> "PLAN";
            default -> "PROGRESS_NOTES";
        };
    }

    private AmendmentHistoryChangeVM fieldChange(
            String module,
            String operationType,
            Long recordId,
            String changedBy,
            java.time.Instant changedAt,
            String field,
            String oldValue,
            String newValue
    ) {
        AmendmentHistoryAction action = "INSERT".equalsIgnoreCase(operationType)
                ? AmendmentHistoryAction.ADDED
                : AmendmentHistoryAction.CHANGED;
        return change(
                module,
                sheetCode(module),
                action,
                recordId,
                changedBy,
                changedAt,
                List.of(new AmendmentHistoryFieldChangeVM(field, oldValue, newValue))
        );
    }

    private AmendmentHistoryAction progressNoteAction(String rawAction, JsonNode payload) {
        String operation = rawAction == null ? "" : rawAction.trim().toUpperCase(Locale.ROOT);
        if ("INSERT".equals(operation)) {
            return AmendmentHistoryAction.ADDED;
        }
        if ("DELETE".equals(operation)) {
            return AmendmentHistoryAction.REMOVED;
        }
        if (becameCancelled(payload)) {
            return AmendmentHistoryAction.CANCELLED;
        }
        return AmendmentHistoryAction.CHANGED;
    }

    private boolean belongsToEncounter(JsonNode payload, Long encounterId) {
        if (payload == null || encounterId == null) {
            return true;
        }
        Long rootEncounterId = longValue(payload.get("encounter_id"));
        if (rootEncounterId != null) {
            return encounterId.equals(rootEncounterId);
        }
        Long oldEncounterId = payload.get("old") == null ? null : longValue(payload.get("old").get("encounter_id"));
        Long newEncounterId = payload.get("new") == null ? null : longValue(payload.get("new").get("encounter_id"));
        if (oldEncounterId != null && !encounterId.equals(oldEncounterId)) {
            return false;
        }
        return newEncounterId == null || encounterId.equals(newEncounterId);
    }

    private static Long longValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        if (node.isTextual() && node.asText().matches("\\d+")) {
            return Long.valueOf(node.asText());
        }
        return null;
    }

    private boolean becameCancelled(JsonNode payload) {
        if (payload == null || !payload.has("old") || !payload.has("new")) {
            return false;
        }
        boolean dateSet = blank(textAt(payload, "old", "cancelled_date"))
                && !blank(textAt(payload, "new", "cancelled_date"));
        boolean reasonSet = blank(textAt(payload, "old", "cancellation_reason"))
                && !blank(textAt(payload, "new", "cancellation_reason"));
        return dateSet || reasonSet;
    }

    private void addVitalSigns(
            List<AmendmentHistoryChangeVM> changes,
            VitalSignsLog log,
            Long encounterId
    ) {
        if (log == null) {
            return;
        }
        AmendmentHistoryChangeVM change = vitalSigns(log, encounterId);
        if (change != null) {
            changes.add(change);
        }
    }

    private boolean isPureDeactivation(VitalSignsLog log, Long encounterId) {
        if (log == null || !"UPDATE".equalsIgnoreCase(actionOf(log))) {
            return false;
        }
        JsonNode payload = read(log.getPayload());
        if (payload == null || !payload.has("old") || !payload.has("new") || !belongsToEncounter(payload, encounterId)) {
            return false;
        }
        JsonNode oldNode = payload.get("old");
        JsonNode newNode = payload.get("new");
        if (!"true".equals(text(oldNode.get("is_active"))) || !"false".equals(text(newNode.get("is_active")))) {
            return false;
        }
        return diff(oldNode, newNode, VITAL_SIGNS_REPLACEMENT_KEYS).isEmpty();
    }

    private boolean isFollowingReplacement(VitalSignsLog deactivation, VitalSignsLog insert, Long encounterId) {
        if (insert == null || !"INSERT".equalsIgnoreCase(actionOf(insert))) {
            return false;
        }
        if (insert.getVitalSignsId() == null
                || insert.getVitalSignsId().equals(deactivation.getVitalSignsId())
                || !isNotBefore(deactivation, insert)) {
            return false;
        }
        JsonNode updated = read(deactivation.getPayload());
        JsonNode inserted = read(insert.getPayload());
        if (!belongsToEncounter(updated, encounterId) || !belongsToEncounter(inserted, encounterId)) {
            return false;
        }
        Long previousEncounterId = encounterIdOf(updated);
        Long insertedEncounterId = encounterIdOf(inserted);
        return previousEncounterId != null && previousEncounterId.equals(insertedEncounterId);
    }

    private AmendmentHistoryChangeVM replacementChange(VitalSignsLog deactivation, VitalSignsLog insert) {
        JsonNode updated = read(deactivation.getPayload());
        JsonNode oldNode = updated == null ? null : updated.get("old");
        JsonNode newNode = read(insert.getPayload());
        List<AmendmentHistoryFieldChangeVM> fields = diff(oldNode, newNode, VITAL_SIGNS_REPLACEMENT_KEYS);
        if (fields.isEmpty()) {
            return null;
        }
        return change(
                VITAL_SIGNS,
                "VITAL_SIGNS",
                AmendmentHistoryAction.CHANGED,
                insert.getVitalSignsId(),
                firstText(insert.getLastModifiedBy(), insert.getCreatedBy()),
                insert.getCreatedDate(),
                fields
        );
    }

    private static String actionOf(VitalSignsLog log) {
        return log.getAction() == null ? "" : log.getAction().trim();
    }

    private static boolean isNotBefore(VitalSignsLog earlier, VitalSignsLog later) {
        if (earlier.getCreatedDate() != null && later.getCreatedDate() != null) {
            int byTime = earlier.getCreatedDate().compareTo(later.getCreatedDate());
            if (byTime > 0) {
                return false;
            }
            if (byTime < 0) {
                return true;
            }
        }
        if (earlier.getId() != null && later.getId() != null) {
            return earlier.getId() <= later.getId();
        }
        return true;
    }

    private Long encounterIdOf(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        Long rootEncounterId = longValue(payload.get("encounter_id"));
        if (rootEncounterId != null) {
            return rootEncounterId;
        }
        Long oldEncounterId = payload.get("old") == null ? null : longValue(payload.get("old").get("encounter_id"));
        if (oldEncounterId != null) {
            return oldEncounterId;
        }
        return payload.get("new") == null ? null : longValue(payload.get("new").get("encounter_id"));
    }

    private List<AmendmentHistoryFieldChangeVM> diff(JsonNode oldNode, JsonNode newNode) {
        return diff(oldNode, newNode, Set.of());
    }

    private List<AmendmentHistoryFieldChangeVM> diff(JsonNode oldNode, JsonNode newNode, Set<String> extraIgnored) {
        Map<String, AmendmentHistoryFieldChangeVM> changes = new LinkedHashMap<>();
        if (oldNode != null && oldNode.isObject()) {
            Iterator<String> names = oldNode.fieldNames();
            while (names.hasNext()) {
                putDifference(changes, names.next(), oldNode, newNode, extraIgnored);
            }
        }
        if (newNode != null && newNode.isObject()) {
            Iterator<String> names = newNode.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!changes.containsKey(name)) {
                    putDifference(changes, name, oldNode, newNode, extraIgnored);
                }
            }
        }
        return List.copyOf(changes.values());
    }

    private void putDifference(
            Map<String, AmendmentHistoryFieldChangeVM> changes,
            String key,
            JsonNode oldNode,
            JsonNode newNode,
            Set<String> extraIgnored
    ) {
        if (TECHNICAL_KEYS.contains(key) || extraIgnored.contains(key)) {
            return;
        }
        String oldValue = text(oldNode == null ? null : oldNode.get(key));
        String newValue = text(newNode == null ? null : newNode.get(key));
        if (java.util.Objects.equals(oldValue, newValue)) {
            return;
        }
        changes.put(key, new AmendmentHistoryFieldChangeVM(label(key), oldValue, newValue));
    }

    private List<AmendmentHistoryFieldChangeVM> snapshot(JsonNode node, boolean added) {
        if (node == null || !node.isObject() || (node.has("old") && node.has("new"))) {
            return List.of();
        }
        List<AmendmentHistoryFieldChangeVM> fields = new ArrayList<>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            if (TECHNICAL_KEYS.contains(key)) {
                continue;
            }
            String value = text(node.get(key));
            if (blank(value)) {
                continue;
            }
            fields.add(new AmendmentHistoryFieldChangeVM(
                    label(key),
                    added ? null : value,
                    added ? value : null
            ));
        }
        return fields;
    }

    private void addIfPresent(
            List<AmendmentHistoryFieldChangeVM> fields,
            String field,
            String oldValue,
            String newValue
    ) {
        if (java.util.Objects.equals(oldValue, newValue)) {
            return;
        }
        fields.add(new AmendmentHistoryFieldChangeVM(field, oldValue, newValue));
    }

    private JsonNode read(String payload) {
        if (blank(payload)) {
            return null;
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            LOG.warn("Amendment history could not read an audit payload");
            return null;
        }
    }

    private static String textAt(JsonNode payload, String side, String field) {
        if (payload == null || payload.get(side) == null) {
            return null;
        }
        return text(payload.get(side).get(field));
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            String value = node.asText();
            return value.isBlank() ? null : value;
        }
        return node.toString();
    }

    private static String label(String key) {
        if (key == null || key.isBlank()) {
            return key;
        }
        String known = LABELS.get(key);
        if (known != null) {
            return known;
        }
        String spaced = key.replace('_', ' ');
        StringBuilder label = new StringBuilder();
        for (String word : spaced.split("(?<=[a-z])(?=[A-Z])| ")) {
            if (word.isBlank()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                label.append(word.substring(1));
            }
        }
        return label.toString();
    }

    private static String firstText(String preferred, String fallback) {
        return blank(preferred) ? fallback : preferred;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
