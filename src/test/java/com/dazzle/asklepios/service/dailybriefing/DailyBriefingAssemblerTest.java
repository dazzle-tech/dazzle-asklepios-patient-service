package com.dazzle.asklepios.service.dailybriefing;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Breakdown;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Coverage;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Indicator;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.ValueState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyBriefingAssemblerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Riyadh");
    private static final LocalDate CURRENT = LocalDate.of(2026, 11, 3);
    private static final Instant NOW = CURRENT.atTime(9, 0).atZone(ZONE).toInstant();

    @Test
    void percentageIsOmittedWhenBaselineIsZero() {
        DailyBriefingCalculator.ComparisonResult result = DailyBriefingCalculator.compare(12L, 0L);
        assertEquals(new BigDecimal("12"), result.absoluteChange());
        assertNull(result.percentageChange());
        assertEquals("BASELINE_ZERO", result.percentageOmittedReason());
    }

    @Test
    void noShowRateIsOmittedWhenNothingWasEligible() {
        DailyBriefingCalculator.RateResult result = DailyBriefingCalculator.rate(0, 0);
        assertNull(result.rate());
        assertEquals("BASELINE_ZERO", result.omittedReason());
    }

    @Test
    void patientIsCountedOnceWhenTheyHaveTwoEncounters() {
        List<DailyBriefingFacts.EncounterRow> rows = List.of(
                encounter(1L, 10L, EncounterReason.FIRST_TIME_VISIT, TreatmentStatus.COMPLETED),
                encounter(2L, 10L, EncounterReason.FOLLOW_UP, TreatmentStatus.COMPLETED),
                encounter(3L, 11L, EncounterReason.FOLLOW_UP, TreatmentStatus.COMPLETED)
        );
        DailyBriefingCalculator.PatientSplit split = DailyBriefingCalculator.classifyPatients(rows);
        assertEquals(2, split.uniquePatients());
        assertEquals(1, split.newPatients());
        assertEquals(1, split.followUpPatients());
    }

    @Test
    void netBillingDoesNotSubtractCancellationsOrCollections() {
        assertEquals(new BigDecimal("70"), DailyBriefingCalculator.netBilling(
                new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("10"), new BigDecimal("0")));
    }

    @Test
    void touchingAppointmentsAreNotConflicts() {
        Instant start = NOW;
        List<DailyBriefingFacts.AppointmentRow> rows = List.of(
                appointment(1L, 7L, start, start.plusSeconds(600)),
                appointment(2L, 7L, start.plusSeconds(600), start.plusSeconds(1200))
        );
        assertTrue(DailyBriefingCalculator.conflicts(rows).isEmpty());
    }

    @Test
    void overlappingAppointmentsAreConflicts() {
        List<DailyBriefingFacts.AppointmentRow> rows = List.of(
                appointment(1L, 7L, NOW, NOW.plusSeconds(600)),
                appointment(2L, 7L, NOW.plusSeconds(300), NOW.plusSeconds(900))
        );
        assertEquals(1, DailyBriefingCalculator.conflicts(rows).size());
    }

    @Test
    void reportKeepsMoneyStreamsSeparateAndDoesNotInventCriticalResults() {
        DailyBriefingFacts facts = DailyBriefingFacts.builder()
                .previousEncounters(List.of())
                .openEncounters(List.of())
                .billing(new DailyBriefingFacts.BillingSnapshot(
                        List.of(new DailyBriefingFacts.ChargeRow("SAR", new BigDecimal("100"), new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("90"), 1)),
                        List.of(new DailyBriefingFacts.MoneyRow("SAR", new BigDecimal("40"), 1, null)),
                        List.of(),
                        List.of(),
                        0L,
                        List.of(new DailyBriefingFacts.MoneyRow("SAR", new BigDecimal("15"), 1, "CASH"), new DailyBriefingFacts.MoneyRow("SAR", new BigDecimal("5"), 1, "CREDIT_CARD")),
                        0L,
                        List.of(new DailyBriefingFacts.MoneyRow("SAR", new BigDecimal("30"), 1, "INSURANCE")),
                        List.of(),
                        0L,
                        List.of(new DailyBriefingFacts.MoneyRow("SAR", new BigDecimal("60"), 1, "PATIENT"), new DailyBriefingFacts.MoneyRow("SAR", new BigDecimal("30"), 1, "INSURANCE")),
                        List.of(new DailyBriefingFacts.MoneyRow("SAR", new BigDecimal("999"), 1, null))
                ))
                .urgentCare(new DailyBriefingFacts.UrgentCareSnapshot(false, null, null, null, null, null, null, null, null, null, null, null, null, null))
                .build();

        DailyBriefingResponse report = DailyBriefingAssembler.assemble(context(), facts);
        Indicator net = indicator(report, "NET_BILLING");
        Indicator collections = indicator(report, "PATIENT_COLLECTIONS");
        Indicator invoices = indicator(report, "ISSUED_INVOICES");
        Indicator critical = indicator(report, "CRITICAL_RESULTS");

        assertEquals(new BigDecimal("90"), net.amount());
        assertEquals(new BigDecimal("20"), collections.amount());
        assertEquals(new BigDecimal("999"), invoices.amount());
        assertEquals(ValueState.UNAVAILABLE, critical.valueState());
        assertNull(critical.count());
        assertFalse(report.urgentCare().included());
        assertTrue(report.actions().stream().allMatch(action -> !action.ownerKnown() && !action.modifiesRecords() && action.requiresReview()));
        assertTrue(report.keyIndicators().stream().noneMatch(item -> item.code().equals("OCCUPANCY_RATE") && item.valueState() == ValueState.VALUE));
    }

    @Test
    void practitionerAndSpecialtyComeFromThePractitionerRecord() {
        DailyBriefingFacts facts = DailyBriefingFacts.builder()
                .previousEncounters(List.of(encounter(1L, 10L, EncounterReason.FIRST_TIME_VISIT, TreatmentStatus.COMPLETED)))
                .build();
        DailyBriefingResponse report = DailyBriefingAssembler.assemble(context(), facts, Map.of(9L, new PractitionerLabel(true, "Sara Nasser", "Cardiology")));
        Breakdown practitioners = breakdown(report, "BY_PRACTITIONER");
        Breakdown specialty = breakdown(report, "BY_SPECIALTY");

        assertEquals(Coverage.COMPLETE, practitioners.coverage());
        assertEquals("Sara Nasser", practitioners.rows().get(0).label());
        assertEquals(Coverage.COMPLETE, specialty.coverage());
        assertEquals("Cardiology", specialty.rows().get(0).label());
        assertTrue(report.encounters().breakdowns().stream().noneMatch(item -> item.code().equals("BY_BRANCH")));
    }

    @Test
    void failedReadIsNotPresentedAsZero() {
        DailyBriefingResponse report = DailyBriefingAssembler.notCalculated(context(), "SPECIALTY_LOOKUP_FAILED");
        assertEquals("NOT_CALCULATED", report.issuance().status());
        assertTrue(report.keyIndicators().stream().allMatch(indicator -> indicator.valueState() == ValueState.UNAVAILABLE));
        assertTrue(report.keyIndicators().stream().allMatch(indicator -> indicator.count() == null));
    }

    private static Breakdown breakdown(DailyBriefingResponse report, String code) {
        return report.encounters().breakdowns().stream().filter(item -> item.code().equals(code)).findFirst().orElseThrow();
    }

    private static Indicator indicator(DailyBriefingResponse report, String code) {
        return report.keyIndicators().stream().filter(item -> item.code().equals(code)).findFirst().orElseThrow();
    }

    private static BriefingContext context() {
        return new BriefingContext(
                "en", "Main Hospital", 1L, null, null, null, CURRENT, "Asia/Riyadh", "FACILITY",
                CURRENT, CURRENT.minusDays(1), CURRENT.minusDays(8), NOW, NOW, true, ZONE,
                24, 15, 30, 240, "test"
        );
    }

    private static DailyBriefingFacts.EncounterRow encounter(Long id, Long patientId, EncounterReason reason, TreatmentStatus status) {
        return new DailyBriefingFacts.EncounterRow(id, patientId, 3L, "Clinic", 9L, reason, status, EncounterBillingStatus.OPEN,
                EncounterType.CLINIC, CURRENT.minusDays(1), null, null, null, true, null);
    }

    private static DailyBriefingFacts.AppointmentRow appointment(Long id, Long practitionerId, Instant start, Instant end) {
        return new DailyBriefingFacts.AppointmentRow(id, AppointmentStatus.BOOKED, start, end, practitionerId, true);
    }
}
