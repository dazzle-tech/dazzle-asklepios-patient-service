package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ConsultationLog;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientEncounterFieldAudit;
import com.dazzle.asklepios.domain.ProgressNoteLog;
import com.dazzle.asklepios.domain.VitalSignsLog;
import com.dazzle.asklepios.domain.enumeration.AmendmentHistoryAction;
import com.dazzle.asklepios.web.rest.vm.patientEncounter.AmendmentHistoryChangeVM;
import com.dazzle.asklepios.web.rest.vm.patientEncounter.AmendmentHistoryFieldChangeVM;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmendmentHistoryNormalizerVitalSignsTest {

    private static final Long ENCOUNTER_ID = 69L;
    private static final Instant SAVED_AT = Instant.parse("2026-10-06T09:41:13.870Z");

    private final AmendmentHistoryNormalizer normalizer = new AmendmentHistoryNormalizer();

    @Test
    void session12ReplacementShowsOnlyTheChangedDiastolic() {
        List<AmendmentHistoryChangeVM> changes = normalizer.vitalSigns(List.of(
                deactivation(11L, 8L, SAVED_AT, reading(8, 77, "37.0", 77, "Functional Status\n", true)),
                insertion(12L, 9L, SAVED_AT, reading(9, 77, "37.0", 100, "Functional Status\n", true))
        ), ENCOUNTER_ID);

        assertEquals(1, changes.size());
        AmendmentHistoryChangeVM change = changes.get(0);
        assertEquals("Vital Signs", change.module());
        assertEquals(AmendmentHistoryAction.CHANGED, change.action());
        assertEquals(9L, change.recordId());
        assertEquals(1, change.changes().size());
        assertField(change.changes().get(0), "Blood Pressure Diastolic", "77", "100");
    }

    @Test
    void multipleClinicalFieldsKeepTheirOldAndNewValues() {
        List<AmendmentHistoryChangeVM> changes = normalizer.vitalSigns(List.of(
                deactivation(1L, 8L, SAVED_AT, reading(8, 70, "36.5", 77, "Before", true)),
                insertion(2L, 9L, SAVED_AT, reading(9, 77, "37.0", 100, "After", true))
        ), ENCOUNTER_ID);

        assertEquals(1, changes.size());
        assertEquals(AmendmentHistoryAction.CHANGED, changes.get(0).action());
        assertEquals(4, changes.get(0).changes().size());
        assertField(field(changes.get(0), "Heart Rate"), "Heart Rate", "70", "77");
        assertField(field(changes.get(0), "Temperature"), "Temperature", "36.5", "37.0");
        assertField(field(changes.get(0), "Blood Pressure Diastolic"), "Blood Pressure Diastolic", "77", "100");
        assertField(field(changes.get(0), "Notes"), "Notes", "Before", "After");
    }

    @Test
    void deactivationWithNoClinicalDifferenceEmitsNothing() {
        List<AmendmentHistoryChangeVM> changes = normalizer.vitalSigns(List.of(
                deactivation(11L, 8L, SAVED_AT, reading(8, 77, "37.0", 77, "Functional Status\n", true)),
                insertion(12L, 9L, SAVED_AT, reading(9, 77, "37.0", 77, "Functional Status\n", true))
        ), ENCOUNTER_ID);

        assertTrue(changes.isEmpty());
    }

    @Test
    void insertWithoutAPredecessorStaysAdded() {
        List<AmendmentHistoryChangeVM> changes = normalizer.vitalSigns(List.of(
                insertion(8L, 7L, SAVED_AT, reading(7, 77, "37.0", 99, "Functional Status\n", true))
        ), ENCOUNTER_ID);

        assertEquals(1, changes.size());
        assertEquals(AmendmentHistoryAction.ADDED, changes.get(0).action());
        assertField(field(changes.get(0), "Heart Rate"), "Heart Rate", null, "77");
        assertField(field(changes.get(0), "Blood Pressure Diastolic"), "Blood Pressure Diastolic", null, "99");
    }

    @Test
    void technicalTimestampOnDeactivationDoesNotHideTheClinicalChange() {
        String oldReading = reading(11, 77, "37.0", 100, "Functional Status\n", true)
                .replace("}", ",\"last_modified_date\":\"2026-10-06T11:09:34.245688\"}");
        String retired = oldReading.replace("\"is_active\":true", "\"is_active\":false")
                .replace("2026-10-06T11:09:34.245688", "2026-10-06T11:29:08.67991");
        String inserted = reading(12, 88, "37.5", 100, "Functional Status\n", true);
        List<AmendmentHistoryChangeVM> changes = normalizer.vitalSigns(List.of(
                log(17L, 11L, "UPDATE", SAVED_AT, "{\"old\":" + oldReading + ",\"new\":" + retired + "}"),
                insertion(18L, 12L, SAVED_AT, inserted)
        ), ENCOUNTER_ID);

        assertEquals(1, changes.size());
        assertEquals(AmendmentHistoryAction.CHANGED, changes.get(0).action());
        assertEquals(2, changes.get(0).changes().size());
        assertField(field(changes.get(0), "Heart Rate"), "Heart Rate", "77", "88");
        assertField(field(changes.get(0), "Temperature"), "Temperature", "37.0", "37.5");
        assertTrue(changes.get(0).changes().stream().noneMatch(row -> "Active".equals(row.field())));
    }

    @Test
    void twoSavesPairEachDeactivationWithItsOwnInsert() {
        Instant first = SAVED_AT.minusSeconds(30);
        List<AmendmentHistoryChangeVM> changes = normalizer.vitalSigns(List.of(
                deactivation(1L, 8L, first, reading(8, 70, "36.5", 77, "A", true)),
                insertion(2L, 9L, first, reading(9, 75, "36.5", 77, "A", true)),
                deactivation(3L, 9L, SAVED_AT, reading(9, 75, "36.5", 77, "A", true)),
                insertion(4L, 10L, SAVED_AT, reading(10, 77, "36.5", 77, "A", true))
        ), ENCOUNTER_ID);

        assertEquals(2, changes.size());
        assertEquals(9L, changes.get(0).recordId());
        assertEquals(AmendmentHistoryAction.CHANGED, changes.get(0).action());
        assertField(changes.get(0).changes().get(0), "Heart Rate", "70", "75");
        assertEquals(10L, changes.get(1).recordId());
        assertEquals(AmendmentHistoryAction.CHANGED, changes.get(1).action());
        assertField(changes.get(1).changes().get(0), "Heart Rate", "75", "77");
    }

    @Test
    void otherAuditDomainsStayOnTheirOwnActions() {
        ProgressNoteLog note = new ProgressNoteLog();
        note.setAction("UPDATE");
        note.setProgressNoteId(4L);
        note.setOldNoteText("Before");
        note.setNewNoteText("After");
        note.setCreatedBy("admin");
        AmendmentHistoryChangeVM progressNote = normalizer.progressNote(note);
        assertEquals(AmendmentHistoryAction.CHANGED, progressNote.action());
        assertEquals("Before", progressNote.changes().get(0).oldValue());
        assertEquals("After", progressNote.changes().get(0).newValue());

        ConsultationLog consultationLog = new ConsultationLog();
        consultationLog.setAction("INSERT");
        consultationLog.setConsultationId(3L);
        consultationLog.setCreatedBy("admin");
        consultationLog.setPayload("{\"id\":3,\"encounter_id\":69,\"consultation_content\":\"New consult\"}");
        AmendmentHistoryChangeVM consultation = normalizer.consultation(consultationLog, ENCOUNTER_ID);
        assertEquals(AmendmentHistoryAction.ADDED, consultation.action());
        assertNull(field(consultation, "Consultation Content").oldValue());
        assertEquals("New consult", field(consultation, "Consultation Content").newValue());

        PatientEncounter encounter = new PatientEncounter();
        encounter.setId(ENCOUNTER_ID);
        PatientEncounterFieldAudit narrative = PatientEncounterFieldAudit.builder()
                .patientEncounter(encounter)
                .fieldName("chiefComplaint")
                .operationType("UPDATE")
                .oldValue("Before")
                .newValue("After")
                .logBy("admin")
                .logDate(SAVED_AT)
                .build();
        AmendmentHistoryChangeVM chiefComplaint = normalizer.narrative(narrative, ENCOUNTER_ID);
        assertEquals(AmendmentHistoryAction.CHANGED, chiefComplaint.action());
        assertEquals("Chief Complaint", chiefComplaint.changes().get(0).field());
        assertEquals("Before", chiefComplaint.changes().get(0).oldValue());
        assertEquals("After", chiefComplaint.changes().get(0).newValue());
    }

    private static VitalSignsLog deactivation(Long logId, Long vitalSignsId, Instant at, String oldReading) {
        return log(logId, vitalSignsId, "UPDATE", at, "{\"old\":" + oldReading + ",\"new\":" + deactivated(oldReading) + "}");
    }

    private static VitalSignsLog insertion(Long logId, Long vitalSignsId, Instant at, String reading) {
        return log(logId, vitalSignsId, "INSERT", at, reading);
    }

    private static VitalSignsLog log(Long logId, Long vitalSignsId, String action, Instant at, String payload) {
        VitalSignsLog log = new VitalSignsLog();
        log.setId(logId);
        log.setVitalSignsId(vitalSignsId);
        log.setAction(action);
        log.setCreatedBy("admin");
        log.setCreatedDate(at);
        log.setPayload(payload);
        log.setReopenSessionId(12L);
        return log;
    }

    private static String reading(
            long id,
            int heartRate,
            String temperature,
            int diastolic,
            String notes,
            boolean active
    ) {
        return "{"
                + "\"id\":" + id + ","
                + "\"encounter_id\":69,"
                + "\"heart_rate\":" + heartRate + ","
                + "\"temperature\":" + temperature + ","
                + "\"blood_pressure_diastolic\":" + diastolic + ","
                + "\"notes\":\"" + notes.replace("\n", "\\n") + "\","
                + "\"is_active\":" + active + ","
                + "\"is_triage\":false"
                + "}";
    }

    private static String deactivated(String reading) {
        return reading.replace("\"is_active\":true", "\"is_active\":false");
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
