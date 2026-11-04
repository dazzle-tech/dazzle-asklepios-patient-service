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

class AmendmentHistoryNormalizerUnifiedReplacementTest {

    private static final Long ENCOUNTER_ID = 69L;
    private static final long PREVIOUS_SESSION = 13L;
    private static final long CURRENT_SESSION = 14L;
    private static final Instant PREVIOUS_AT = Instant.parse("2026-10-06T10:28:41.679Z");
    private static final Instant DEACTIVATED_AT = Instant.parse("2026-10-06T10:42:28.758Z");
    private static final Instant REPLACED_AT = Instant.parse("2026-10-06T10:42:28.771Z");

    private final AmendmentHistoryNormalizer normalizer = new AmendmentHistoryNormalizer();

    @Test
    void session14BodyMeasurementsBecomesOneClinicalChange() {
        Map<Long, List<AmendmentHistoryChangeVM>> history = normalizer.unifiedHistory(List.of(
                added(16L, PREVIOUS_SESSION, PREVIOUS_AT, "BODY_MEASUREMENTS", 10L, "{\"height\":180,\"weight\":60,\"isActive\":true}"),
                deactivated(27L, CURRENT_SESSION, DEACTIVATED_AT, "BODY_MEASUREMENTS", 10L),
                added(28L, CURRENT_SESSION, REPLACED_AT, "BODY_MEASUREMENTS", 11L, "{\"height\":186,\"weight\":76,\"isActive\":true}")
        ), ENCOUNTER_ID);

        assertEquals(AmendmentHistoryAction.ADDED, history.get(PREVIOUS_SESSION).get(0).action());
        List<AmendmentHistoryChangeVM> current = history.get(CURRENT_SESSION);
        assertEquals(1, current.size());
        AmendmentHistoryChangeVM change = current.get(0);
        assertEquals("Body Measurements", change.module());
        assertEquals("BODY_MEASUREMENTS", change.medicalSheet());
        assertEquals(AmendmentHistoryAction.CHANGED, change.action());
        assertEquals(11L, change.recordId());
        assertEquals(2, change.changes().size());
        assertField(field(change, "Height"), "Height", "180", "186");
        assertField(field(change, "Weight"), "Weight", "60", "76");
        assertTrue(change.changes().stream().noneMatch(row -> "Is Active".equals(row.field())));
    }

    @Test
    void unchangedClinicalFieldsAreOmitted() {
        List<AmendmentHistoryChangeVM> current = current(normalizer.unifiedHistory(List.of(
                added(1L, PREVIOUS_SESSION, PREVIOUS_AT, "BODY_MEASUREMENTS", 10L, "{\"height\":180,\"weight\":60,\"isActive\":true}"),
                deactivated(2L, CURRENT_SESSION, DEACTIVATED_AT, "BODY_MEASUREMENTS", 10L),
                added(3L, CURRENT_SESSION, REPLACED_AT, "BODY_MEASUREMENTS", 11L, "{\"height\":180,\"weight\":76,\"isActive\":true}")
        ), ENCOUNTER_ID));

        assertEquals(1, current.size());
        assertEquals(1, current.get(0).changes().size());
        assertField(current.get(0).changes().get(0), "Weight", "60", "76");
    }

    @Test
    void technicalOnlyReplacementEmitsNothing() {
        Map<Long, List<AmendmentHistoryChangeVM>> history = normalizer.unifiedHistory(List.of(
                added(1L, PREVIOUS_SESSION, PREVIOUS_AT, "BODY_MEASUREMENTS", 10L, "{\"height\":180,\"weight\":60,\"isActive\":true}"),
                deactivated(2L, CURRENT_SESSION, DEACTIVATED_AT, "BODY_MEASUREMENTS", 10L),
                added(3L, CURRENT_SESSION, REPLACED_AT, "BODY_MEASUREMENTS", 11L, "{\"height\":180,\"weight\":60,\"isActive\":true}")
        ), ENCOUNTER_ID);

        assertTrue(history.get(CURRENT_SESSION) == null || history.get(CURRENT_SESSION).isEmpty());
    }

    @Test
    void twoReplacementsStayPairedInOrder() {
        Instant secondDeactivation = REPLACED_AT.plusMillis(20);
        Instant secondInsert = REPLACED_AT.plusMillis(30);
        List<AmendmentHistoryChangeVM> current = current(normalizer.unifiedHistory(List.of(
                added(1L, PREVIOUS_SESSION, PREVIOUS_AT, "BODY_MEASUREMENTS", 10L, "{\"height\":170,\"isActive\":true}"),
                deactivated(2L, CURRENT_SESSION, DEACTIVATED_AT, "BODY_MEASUREMENTS", 10L),
                added(3L, CURRENT_SESSION, REPLACED_AT, "BODY_MEASUREMENTS", 11L, "{\"height\":175,\"isActive\":true}"),
                deactivated(4L, CURRENT_SESSION, secondDeactivation, "BODY_MEASUREMENTS", 11L),
                added(5L, CURRENT_SESSION, secondInsert, "BODY_MEASUREMENTS", 12L, "{\"height\":180,\"isActive\":true}")
        ), ENCOUNTER_ID));

        assertEquals(2, current.size());
        assertEquals(11L, current.get(0).recordId());
        assertField(field(current.get(0), "Height"), "Height", "170", "175");
        assertEquals(12L, current.get(1).recordId());
        assertField(field(current.get(1), "Height"), "Height", "175", "180");
        assertTrue(current.stream().allMatch(change -> change.action() == AmendmentHistoryAction.CHANGED));
    }

    @Test
    void earlierDeltaIsTheBaseline() {
        List<AmendmentHistoryChangeVM> current = current(normalizer.unifiedHistory(List.of(
                added(1L, PREVIOUS_SESSION, PREVIOUS_AT, "BODY_MEASUREMENTS", 10L, "{\"height\":175,\"weight\":60,\"isActive\":true}"),
                changed(2L, PREVIOUS_SESSION, PREVIOUS_AT.plusSeconds(5), "BODY_MEASUREMENTS", 10L, "{\"height\":175}", "{\"height\":180}"),
                deactivated(3L, CURRENT_SESSION, DEACTIVATED_AT, "BODY_MEASUREMENTS", 10L),
                added(4L, CURRENT_SESSION, REPLACED_AT, "BODY_MEASUREMENTS", 11L, "{\"height\":186,\"weight\":60,\"isActive\":true}")
        ), ENCOUNTER_ID));

        assertEquals(1, current.size());
        assertEquals(1, current.get(0).changes().size());
        assertField(field(current.get(0), "Height"), "Height", "180", "186");
    }

    @Test
    void bodyMeasurementWithoutPredecessorStaysAdded() {
        List<AmendmentHistoryChangeVM> current = current(normalizer.unifiedHistory(List.of(
                added(28L, CURRENT_SESSION, REPLACED_AT, "BODY_MEASUREMENTS", 11L, "{\"height\":186,\"weight\":76,\"isActive\":true}")
        ), ENCOUNTER_ID));

        assertEquals(1, current.size());
        assertEquals(AmendmentHistoryAction.ADDED, current.get(0).action());
        assertNull(field(current.get(0), "Height").oldValue());
        assertEquals("186", field(current.get(0), "Height").newValue());
    }

    @Test
    void observationsAreVersionedSeparatelyFromReplacement() {
        Map<Long, List<AmendmentHistoryChangeVM>> history = normalizer.unifiedHistory(List.of(
                added(25L, PREVIOUS_SESSION, PREVIOUS_AT, "OBSERVATIONS_COMPLAINTS", 15L,
                        "{\"reasonOfVisit\":\"Reason Of Visit*\\nFunctional Status\\n\",\"isActive\":true}"),
                added(26L, CURRENT_SESSION, DEACTIVATED_AT, "OBSERVATIONS_COMPLAINTS", 16L,
                        "{\"reasonOfVisit\":\"Reason Of Visit*ffdsfds\\nFunctional Status\\n\",\"isActive\":true}")
        ), ENCOUNTER_ID);

        assertEquals(AmendmentHistoryAction.ADDED, history.get(PREVIOUS_SESSION).get(0).action());
        assertEquals(15L, history.get(PREVIOUS_SESSION).get(0).recordId());
        assertEquals(1, history.get(CURRENT_SESSION).size());
        AmendmentHistoryChangeVM change = history.get(CURRENT_SESSION).get(0);
        assertEquals(AmendmentHistoryAction.CHANGED, change.action());
        assertEquals(16L, change.recordId());
        assertEquals(1, change.changes().size());
        assertField(field(change, "Reason Of Visit"), "Reason Of Visit",
                "Reason Of Visit*\nFunctional Status\n",
                "Reason Of Visit*ffdsfds\nFunctional Status\n");
        assertTrue(change.changes().stream().noneMatch(row -> "Is Active".equals(row.field())));
    }

    @Test
    void missingBaselineKeepsTheRawEvents() {
        List<AmendmentHistoryChangeVM> current = current(normalizer.unifiedHistory(List.of(
                deactivated(27L, CURRENT_SESSION, DEACTIVATED_AT, "BODY_MEASUREMENTS", 10L),
                added(28L, CURRENT_SESSION, REPLACED_AT, "BODY_MEASUREMENTS", 11L, "{\"height\":186,\"weight\":76,\"isActive\":true}")
        ), ENCOUNTER_ID));

        assertEquals(2, current.size());
        assertEquals(AmendmentHistoryAction.CHANGED, current.get(0).action());
        assertField(field(current.get(0), "Is Active"), "Is Active", "true", "false");
        assertEquals(AmendmentHistoryAction.ADDED, current.get(1).action());
        assertNull(field(current.get(1), "Height").oldValue());
        assertEquals("186", field(current.get(1), "Height").newValue());
    }

    @Test
    void painReplacementUsesTheClinicalSnapshotOnDeactivation() {
        String retired = "{"
                + "\"isActive\":true,"
                + "\"painLevel\":\"LEVEL_8\","
                + "\"painDegree\":\"SEVERE\","
                + "\"painPattern\":\"868667307992361\","
                + "\"painDescription\":\"Functional Status\\n\","
                + "\"painAssessmentType\":\"NUMERIC\""
                + "}";
        String inserted = "{"
                + "\"isActive\":true,"
                + "\"painLevel\":\"LEVEL_9\","
                + "\"painDegree\":\"SEVERE\","
                + "\"painPattern\":\"868667307992361\","
                + "\"painDescription\":\"Functional Status\\n\","
                + "\"painAssessmentType\":\"FACES\""
                + "}";
        List<AmendmentHistoryChangeVM> current = current(normalizer.unifiedHistory(List.of(
                snapshotDeactivation(39L, CURRENT_SESSION, DEACTIVATED_AT, "PAIN_ASSESSMENT", 6L, retired),
                added(40L, CURRENT_SESSION, REPLACED_AT, "PAIN_ASSESSMENT", 7L, inserted)
        ), ENCOUNTER_ID));

        assertEquals(1, current.size());
        AmendmentHistoryChangeVM change = current.get(0);
        assertEquals("Pain Assessment", change.module());
        assertEquals(AmendmentHistoryAction.CHANGED, change.action());
        assertEquals(7L, change.recordId());
        assertEquals(2, change.changes().size());
        assertField(field(change, "Pain Level"), "Pain Level", "LEVEL_8", "LEVEL_9");
        assertField(field(change, "Pain Assessment Type"), "Pain Assessment Type", "NUMERIC", "FACES");
        assertTrue(change.changes().stream().noneMatch(row ->
                "Is Active".equals(row.field())
                        || "Pain Degree".equals(row.field())
                        || "Pain Pattern".equals(row.field())
                        || "Pain Description".equals(row.field())));
    }

    @Test
    void painDeactivationWithoutClinicalSnapshotKeepsTheRawEvents() {
        List<AmendmentHistoryChangeVM> current = current(normalizer.unifiedHistory(List.of(
                deactivated(39L, CURRENT_SESSION, DEACTIVATED_AT, "PAIN_ASSESSMENT", 6L),
                added(40L, CURRENT_SESSION, REPLACED_AT, "PAIN_ASSESSMENT", 7L,
                        "{\"isActive\":true,\"painLevel\":\"LEVEL_9\",\"painDegree\":\"SEVERE\",\"painAssessmentType\":\"FACES\"}")
        ), ENCOUNTER_ID));

        assertEquals(2, current.size());
        assertEquals(AmendmentHistoryAction.CHANGED, current.get(0).action());
        assertField(field(current.get(0), "Is Active"), "Is Active", "true", "false");
        assertEquals(AmendmentHistoryAction.ADDED, current.get(1).action());
        assertNull(field(current.get(1), "Pain Level").oldValue());
        assertEquals("LEVEL_9", field(current.get(1), "Pain Level").newValue());
    }

    @Test
    void painAssessmentReplacementUsesThePreviousSnapshot() {
        List<AmendmentHistoryChangeVM> current = current(normalizer.unifiedHistory(List.of(
                added(1L, PREVIOUS_SESSION, PREVIOUS_AT, "PAIN_ASSESSMENT", 4L, "{\"painLevel\":\"MILD\",\"isActive\":true}"),
                deactivated(2L, CURRENT_SESSION, DEACTIVATED_AT, "PAIN_ASSESSMENT", 4L),
                added(3L, CURRENT_SESSION, REPLACED_AT, "PAIN_ASSESSMENT", 5L, "{\"painLevel\":\"SEVERE\",\"isActive\":true}")
        ), ENCOUNTER_ID));

        assertEquals(1, current.size());
        assertEquals("Pain Assessment", current.get(0).module());
        assertEquals(AmendmentHistoryAction.CHANGED, current.get(0).action());
        assertField(field(current.get(0), "Pain Level"), "Pain Level", "MILD", "SEVERE");
    }

    @Test
    void additionalMeasurementsUsesTheSnapshotStoredOnDeactivation() {
        String retired = "{\"isActive\":true,\"hearingTest\":\"Pass\",\"ageGroup\":\"INFANT\"}";
        String inserted = "{\"isActive\":true,\"hearingTest\":\"Fail\",\"ageGroup\":\"INFANT\"}";
        List<AmendmentHistoryChangeVM> current = current(normalizer.unifiedHistory(List.of(
                snapshotDeactivation(2L, CURRENT_SESSION, DEACTIVATED_AT, "ADDITIONAL_MEASUREMENTS", 8L, retired),
                added(3L, CURRENT_SESSION, REPLACED_AT, "ADDITIONAL_MEASUREMENTS", 9L, inserted)
        ), ENCOUNTER_ID));

        assertEquals(1, current.size());
        assertEquals("Additional Measurements", current.get(0).module());
        assertEquals(AmendmentHistoryAction.CHANGED, current.get(0).action());
        assertEquals(1, current.get(0).changes().size());
        assertField(field(current.get(0), "Hearing Test"), "Hearing Test", "Pass", "Fail");
        assertTrue(current.get(0).changes().stream().noneMatch(row -> "Is Active".equals(row.field()) || "Age Group".equals(row.field())));
    }

    @Test
    void additionalMeasurementsReplacementUsesThePreviousSnapshot() {
        List<AmendmentHistoryChangeVM> current = current(normalizer.unifiedHistory(List.of(
                added(1L, PREVIOUS_SESSION, PREVIOUS_AT, "ADDITIONAL_MEASUREMENTS", 8L, "{\"hearingTest\":\"Pass\",\"isActive\":true}"),
                deactivated(2L, CURRENT_SESSION, DEACTIVATED_AT, "ADDITIONAL_MEASUREMENTS", 8L),
                added(3L, CURRENT_SESSION, REPLACED_AT, "ADDITIONAL_MEASUREMENTS", 9L, "{\"hearingTest\":\"Fail\",\"isActive\":true}")
        ), ENCOUNTER_ID));

        assertEquals(1, current.size());
        assertEquals("Additional Measurements", current.get(0).module());
        assertEquals(AmendmentHistoryAction.CHANGED, current.get(0).action());
        assertField(field(current.get(0), "Hearing Test"), "Hearing Test", "Pass", "Fail");
    }

    private static List<AmendmentHistoryChangeVM> current(Map<Long, List<AmendmentHistoryChangeVM>> history) {
        return history.getOrDefault(CURRENT_SESSION, List.of());
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

    private static EncounterAmendmentChangeLog snapshotDeactivation(
            long id,
            long sessionId,
            Instant at,
            String sheet,
            long recordId,
            String before
    ) {
        String after = before.replace("\"isActive\":true", "\"isActive\":false");
        return event(id, sessionId, at, "{"
                + "\"medicalSheet\":\"" + sheet + "\","
                + "\"action\":\"CHANGED\","
                + "\"recordId\":" + recordId + ","
                + "\"before\":" + before + ","
                + "\"after\":" + after
                + "}");
    }

    private static EncounterAmendmentChangeLog deactivated(
            long id,
            long sessionId,
            Instant at,
            String sheet,
            long recordId
    ) {
        return event(id, sessionId, at, "{"
                + "\"medicalSheet\":\"" + sheet + "\","
                + "\"action\":\"CHANGED\","
                + "\"recordId\":" + recordId + ","
                + "\"before\":{\"isActive\":true},"
                + "\"after\":{\"isActive\":false}"
                + "}");
    }

    private static EncounterAmendmentChangeLog changed(
            long id,
            long sessionId,
            Instant at,
            String sheet,
            long recordId,
            String before,
            String after
    ) {
        return event(id, sessionId, at, "{"
                + "\"medicalSheet\":\"" + sheet + "\","
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
}
