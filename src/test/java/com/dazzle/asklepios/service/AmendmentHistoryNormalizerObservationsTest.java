package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.EncounterAmendmentChangeLog;
import com.dazzle.asklepios.domain.enumeration.AmendmentHistoryAction;
import com.dazzle.asklepios.web.rest.vm.patientEncounter.AmendmentHistoryChangeVM;
import com.dazzle.asklepios.web.rest.vm.patientEncounter.AmendmentHistoryFieldChangeVM;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmendmentHistoryNormalizerObservationsTest {

    private static final Long ENCOUNTER_ID = 69L;
    private static final long SESSION_14 = 14L;
    private static final long SESSION_15 = 15L;
    private static final Instant RECORD_16_AT = Instant.parse("2026-10-06T10:42:19.433Z");
    private static final Instant RECORD_17_AT = Instant.parse("2026-10-06T11:09:19.642Z");
    private static final Instant RECORD_18_AT = Instant.parse("2026-10-06T11:09:21.510Z");
    private static final Instant RECORD_19_AT = Instant.parse("2026-10-06T11:09:26.867Z");

    private final AmendmentHistoryNormalizer normalizer = new AmendmentHistoryNormalizer();

    @Test
    void firstAuditedSnapshotStaysAdded() {
        List<AmendmentHistoryChangeVM> current = session(normalizer.unifiedHistory(List.of(
                observations(31L, SESSION_15, RECORD_17_AT, 17L, snapshot("A", "X", "Y", "O_NEGATIVE"))
        ), ENCOUNTER_ID), SESSION_15);

        assertEquals(1, current.size());
        AmendmentHistoryChangeVM added = current.get(0);
        assertEquals("Observations / Complaints", added.module());
        assertEquals("OBSERVATIONS_COMPLAINTS", added.medicalSheet());
        assertEquals(AmendmentHistoryAction.ADDED, added.action());
        assertEquals(17L, added.recordId());
        assertEquals("admin", added.changedBy());
        assertEquals(RECORD_17_AT, added.changedAt());
        assertNull(field(added, "Reason Of Visit").oldValue());
        assertEquals("A", field(added, "Reason Of Visit").newValue());
        assertNoLifecycle(added);
    }

    @Test
    void session15KeepsThreeSeparateClinicalChanges() {
        Map<Long, List<AmendmentHistoryChangeVM>> history = normalizer.unifiedHistory(List.of(
                observations(26L, SESSION_14, RECORD_16_AT, 16L, snapshot("A", "X", "Y", "O_NEGATIVE")),
                observations(31L, SESSION_15, RECORD_17_AT, 17L, snapshot("A", "X-last", "Y-last", "O_NEGATIVE")),
                observations(32L, SESSION_15, RECORD_18_AT, 18L, snapshot("B", "X-last", "Y-last", "O_NEGATIVE")),
                observations(33L, SESSION_15, RECORD_19_AT, 19L, snapshot("B", "X-last", "Y-last", "B_NEGATIVE"))
        ), ENCOUNTER_ID);

        assertEquals(AmendmentHistoryAction.ADDED, history.get(SESSION_14).get(0).action());
        assertEquals(16L, history.get(SESSION_14).get(0).recordId());
        List<AmendmentHistoryChangeVM> current = history.get(SESSION_15);
        assertEquals(3, current.size());

        AmendmentHistoryChangeVM first = current.get(0);
        assertEquals(AmendmentHistoryAction.CHANGED, first.action());
        assertEquals(17L, first.recordId());
        assertEquals(RECORD_17_AT, first.changedAt());
        assertEquals("admin", first.changedBy());
        assertEquals(2, first.changes().size());
        assertField(field(first, "Cognitive Check"), "Cognitive Check", "X", "X-last");
        assertField(field(first, "Functional Status"), "Functional Status", "Y", "Y-last");
        assertAbsent(first, "Reason Of Visit", "Blood Group", "Is Active", "By Patient", "Mode Of Arrival");

        AmendmentHistoryChangeVM second = current.get(1);
        assertEquals(AmendmentHistoryAction.CHANGED, second.action());
        assertEquals(18L, second.recordId());
        assertEquals(1, second.changes().size());
        assertField(field(second, "Reason Of Visit"), "Reason Of Visit", "A", "B");
        assertAbsent(second, "Cognitive Check", "Functional Status", "Blood Group", "Is Active");

        AmendmentHistoryChangeVM third = current.get(2);
        assertEquals(AmendmentHistoryAction.CHANGED, third.action());
        assertEquals(19L, third.recordId());
        assertEquals(1, third.changes().size());
        assertField(field(third, "Blood Group"), "Blood Group", "O_NEGATIVE", "B_NEGATIVE");
        assertAbsent(third, "Reason Of Visit", "Cognitive Check", "Functional Status", "Is Active");
    }

    @Test
    void identicalResaveEmitsNothing() {
        Map<Long, List<AmendmentHistoryChangeVM>> history = normalizer.unifiedHistory(List.of(
                observations(26L, SESSION_14, RECORD_16_AT, 16L, snapshot("A", "X", "Y", "O_NEGATIVE")),
                observations(31L, SESSION_15, RECORD_17_AT, 17L, snapshot("A", "X", "Y", "O_NEGATIVE"))
        ), ENCOUNTER_ID);

        assertEquals(1, history.get(SESSION_14).size());
        assertTrue(history.get(SESSION_15) == null || history.get(SESSION_15).isEmpty());
    }

    @Test
    void earlierChangedDeltaIsAppliedBeforeTheNextSnapshot() {
        List<AmendmentHistoryChangeVM> current = session(normalizer.unifiedHistory(List.of(
                observations(26L, SESSION_14, RECORD_16_AT, 16L, snapshot("A", "X", "Y", "O_NEGATIVE")),
                changed(27L, SESSION_14, RECORD_16_AT.plusSeconds(1), 16L,
                        "{\"cognitiveCheck\":\"X\"}",
                        "{\"cognitiveCheck\":\"Z\"}"),
                observations(31L, SESSION_15, RECORD_17_AT, 17L, snapshot("B", "Z", "Y", "O_NEGATIVE"))
        ), ENCOUNTER_ID), SESSION_15);

        assertEquals(1, current.size());
        assertEquals(AmendmentHistoryAction.CHANGED, current.get(0).action());
        assertEquals(1, current.get(0).changes().size());
        assertField(field(current.get(0), "Reason Of Visit"), "Reason Of Visit", "A", "B");
        assertAbsent(current.get(0), "Cognitive Check", "Functional Status", "Blood Group", "Is Active");
    }

    @Test
    void anotherAddedDomainStaysAdded() {
        Map<Long, List<AmendmentHistoryChangeVM>> history = normalizer.unifiedHistory(List.of(
                added(10L, SESSION_14, RECORD_16_AT, "ALLERGIES", 3L, "{\"allergen\":\"PEANUT\",\"isActive\":true}"),
                observations(26L, SESSION_14, RECORD_16_AT.plusMillis(1), 16L, snapshot("A", "X", "Y", "O_NEGATIVE")),
                added(11L, SESSION_15, RECORD_17_AT, "ALLERGIES", 4L, "{\"allergen\":\"LATEX\",\"isActive\":true}"),
                observations(31L, SESSION_15, RECORD_18_AT, 17L, snapshot("B", "X", "Y", "O_NEGATIVE"))
        ), ENCOUNTER_ID);

        AmendmentHistoryChangeVM previousAllergy = history.get(SESSION_14).stream()
                .filter(change -> "ALLERGIES".equals(change.medicalSheet()))
                .findFirst()
                .orElseThrow();
        AmendmentHistoryChangeVM currentAllergy = history.get(SESSION_15).stream()
                .filter(change -> "ALLERGIES".equals(change.medicalSheet()))
                .findFirst()
                .orElseThrow();
        assertEquals(AmendmentHistoryAction.ADDED, previousAllergy.action());
        assertEquals(AmendmentHistoryAction.ADDED, currentAllergy.action());
        assertEquals(4L, currentAllergy.recordId());
        assertNull(field(currentAllergy, "Allergen").oldValue());
        assertEquals("LATEX", field(currentAllergy, "Allergen").newValue());

        AmendmentHistoryChangeVM observation = history.get(SESSION_15).stream()
                .filter(change -> "OBSERVATIONS_COMPLAINTS".equals(change.medicalSheet()))
                .findFirst()
                .orElseThrow();
        assertEquals(AmendmentHistoryAction.CHANGED, observation.action());
        assertField(field(observation, "Reason Of Visit"), "Reason Of Visit", "A", "B");
    }

    private static List<AmendmentHistoryChangeVM> session(
            Map<Long, List<AmendmentHistoryChangeVM>> history,
            long sessionId
    ) {
        return history.getOrDefault(sessionId, List.of());
    }

    private static String snapshot(String reason, String cognitive, String functional, String bloodGroup) {
        return "{"
                + "\"patientId\":7,"
                + "\"encounterId\":69,"
                + "\"reasonOfVisit\":\"" + reason + "\","
                + "\"cognitiveCheck\":\"" + cognitive + "\","
                + "\"functionalStatus\":\"" + functional + "\","
                + "\"bloodGroup\":\"" + bloodGroup + "\","
                + "\"byPatient\":true,"
                + "\"modeOfArrival\":\"AMBULATORY\","
                + "\"isActive\":true"
                + "}";
    }

    private static EncounterAmendmentChangeLog observations(
            long id,
            long sessionId,
            Instant at,
            long recordId,
            String after
    ) {
        return added(id, sessionId, at, "OBSERVATIONS_COMPLAINTS", recordId, after);
    }

    private static EncounterAmendmentChangeLog added(
            long id,
            long sessionId,
            Instant at,
            String sheet,
            long recordId,
            String after
    ) {
        return event(id, sessionId, at, "{"
                + "\"medicalSheet\":\"" + sheet + "\","
                + "\"action\":\"ADDED\","
                + "\"recordId\":" + recordId + ","
                + "\"before\":null,"
                + "\"after\":" + after
                + "}");
    }

    private static EncounterAmendmentChangeLog changed(
            long id,
            long sessionId,
            Instant at,
            long recordId,
            String before,
            String after
    ) {
        return event(id, sessionId, at, "{"
                + "\"medicalSheet\":\"OBSERVATIONS_COMPLAINTS\","
                + "\"action\":\"CHANGED\","
                + "\"recordId\":" + recordId + ","
                + "\"before\":" + before + ","
                + "\"after\":" + after
                + "}");
    }

    private static EncounterAmendmentChangeLog event(long id, long sessionId, Instant at, String changeData) {
        EncounterAmendmentChangeLog log = new EncounterAmendmentChangeLog();
        log.setId(id);
        log.setReopenSessionId(sessionId);
        log.setEncounterId(ENCOUNTER_ID);
        log.setChangedBy("admin");
        log.setChangedAt(at);
        log.setChangeData(changeData);
        return log;
    }

    private static AmendmentHistoryFieldChangeVM field(AmendmentHistoryChangeVM change, String name) {
        return change.changes().stream()
                .filter(row -> name.equals(row.field()))
                .findFirst()
                .orElseThrow();
    }

    private static void assertField(AmendmentHistoryFieldChangeVM field, String name, String oldValue, String newValue) {
        assertEquals(name, field.field());
        assertEquals(oldValue, field.oldValue());
        assertEquals(newValue, field.newValue());
    }

    private static void assertAbsent(AmendmentHistoryChangeVM change, String... names) {
        for (String name : names) {
            assertTrue(change.changes().stream().noneMatch(row -> name.equals(row.field())), name);
        }
    }

    private static void assertNoLifecycle(AmendmentHistoryChangeVM change) {
        assertAbsent(change, "Is Active", "Patient Id", "Encounter Id", "Id");
    }
}
