package com.dazzle.asklepios.service.dailybriefing;

import com.dazzle.asklepios.domain.enumeration.AppointmentStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.ActionItem;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Alert;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.AppliedFilters;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Breakdown;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Comparison;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Coverage;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.DataQuality;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.DimensionRow;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Drill;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.ExecutiveSummary;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.HighlightThresholds;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Indicator;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Issuance;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.MoneyAmount;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Section;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DailyBriefingAssembler {

    private static final String DUPLICATE_POLICY = "A second stored version for the same facility, filters, report date, language, and recipient is rejected unless a supersede reason is supplied.";

    private DailyBriefingAssembler() {
    }

    public static DailyBriefingResponse assemble(BriefingContext context, DailyBriefingFacts facts) {
        Indicators indicators = new Indicators(context);
        Section encounters = encounters(context, facts, indicators);
        Section appointments = appointments(context, facts, indicators);
        Section billing = billing(context, facts, indicators);
        Section insurance = insurance(context, facts, indicators);
        Section waiting = waiting(context, facts, indicators);
        Section completion = completion(context, facts, indicators);
        Section inpatient = inpatient(context, facts, indicators);
        List<Alert> exceptions = exceptions(context, facts, indicators);
        Section comparisons = comparisons(context, indicators);
        List<Indicator> headlines = indicators.headlines();
        String status = status(context, facts, headlines);
        return new DailyBriefingResponse(
                context.language(),
                filters(context),
                thresholds(context, indicators),
                issuance(context, status, facts.failures()),
                summary(context, indicators, headlines, exceptions),
                headlines,
                exceptions,
                actions(exceptions),
                encounters,
                appointments,
                billing,
                insurance,
                waiting,
                completion,
                inpatient,
                comparisons,
                quality(context, facts, indicators)
        );
    }

    public static DailyBriefingResponse notCalculated(BriefingContext context, String reason) {
        Indicators indicators = new Indicators(context);
        indicators.unavailable(
                "REPORT",
                "Daily briefing",
                "الإحاطة اليومية",
                "The briefing was not calculated. No zero is substituted for a figure that was not read.",
                "لم تُحسب الإحاطة. لا يُستبدل الصفر برقم لم يُقرأ.",
                previousPeriod(context, indicators),
                Coverage.NOT_IN_SOURCE,
                reason,
                null,
                true
        );
        Alert alert = alert(indicators, "DATA_NOT_CALCULATED", "HIGH", "OPERATIONAL",
                indicators.pair("The briefing was not calculated.", "لم تُحسب الإحاطة."),
                reason,
                indicators.pair("No operational, financial, or clinical impact was calculated.", "لم يُحسب أثر تشغيلي أو مالي أو سريري."),
                indicators.pair("Restore the missing source, then run the briefing again.", "أصلح مصدر البيانات الناقص ثم أعد تشغيل الإحاطة."));
        List<String> notes = List.of(reason);
        return new DailyBriefingResponse(
                context.language(),
                filters(context),
                thresholds(context, indicators),
                issuance(context, "NOT_CALCULATED", List.of(reason)),
                new ExecutiveSummary(
                        indicators.pair(
                                "No figures are shown because the briefing could not be calculated for the requested filters. " + reason,
                                "لا تُعرض أرقام لأن الإحاطة تعذر حسابها للمرشحات المطلوبة. " + reason
                        ),
                        List.of(),
                        List.of(alert.issue())
                ),
                indicators.headlines(),
                List.of(alert),
                actions(List.of(alert)),
                placeholder(indicators, "ENCOUNTERS", "Encounters and patients", "الزيارات والمرضى", notes),
                placeholder(indicators, "APPOINTMENTS", "Appointments and daily readiness", "المواعيد وجاهزية اليوم", notes),
                placeholder(indicators, "BILLING", "Billing and collections", "الفوترة والتحصيل", notes),
                placeholder(indicators, "INSURANCE", "Insurance, authorizations, and claims", "التأمين والموافقات والمطالبات", notes),
                placeholder(indicators, "WAITING", "Waiting times, delays, and results", "أوقات الانتظار والتأخير والنتائج", notes),
                placeholder(indicators, "COMPLETION", "Encounter completion and unbilled services", "إكمال الزيارة والخدمات غير المفوترة", notes),
                new Section("INPATIENT", indicators.pair("Inpatient and emergency", "التنويم والطوارئ"), false, reason, null, List.of(), List.of(), notes),
                placeholder(indicators, "COMPARISONS", "Comparisons and targets", "المقارنات والأهداف", notes),
                quality(context, DailyBriefingFacts.builder().failure(reason).build(), indicators)
        );
    }

    private static Section encounters(BriefingContext context, DailyBriefingFacts facts, Indicators indicators) {
        indicators.begin("ENCOUNTERS");
        String period = previousPeriod(context, indicators);
        String baseline = comparisonPeriod(context, indicators);
        List<DailyBriefingFacts.EncounterRow> rows = facts.previousEncounters();
        Long allRows = facts.previousEncountersCapped()
                ? facts.previousEncounterTotal()
                : rows == null ? null : (long) rows.size();
        indicators.count("PREVIOUS_DAY_ENCOUNTER_ROWS", "Encounter rows", "صفوف الزيارات",
                "Every encounter dated on the previous facility day, of any status. This is not a patient count.",
                "كل زيارة بتاريخ اليوم السابق بحسب يوم المنشأة، بأي حالة. هذا ليس عدد مرضى.",
                period, allRows, facts.previousEncountersCapped() ? Coverage.PARTIAL : Coverage.COMPLETE, null, null, null, false);

        if (facts.previousEncountersCapped() || rows == null) {
            String reason = facts.previousEncountersCapped() ? "VOLUME_CAP" : "QUERY_FAILED";
            indicators.unavailable("COMPLETED_ENCOUNTERS", "Completed encounters", "الزيارات المكتملة",
                    "Encounters with status completed, closed, or discharged.", "زيارات بحالة مكتملة أو مغلقة أو خروج.", period, Coverage.PARTIAL, reason, null, true);
            indicators.unavailable("UNIQUE_PATIENTS", "Unique patients", "المرضى الفريدون",
                    "Distinct patients among completed encounters. A patient with several encounters is counted once.",
                    "المرضى المميزون بين الزيارات المكتملة. المريض الذي له أكثر من زيارة يُحسب مرة واحدة.", period, Coverage.PARTIAL, reason, null, true);
            indicators.unavailable("NEW_PATIENTS", "New patients", "المرضى الجدد",
                    "Unique completed-encounter patients who had a first-time visit that day. A patient is not also counted as follow-up.",
                    "مرضى الزيارات المكتملة الذين كان لهم زيارة أولى في ذلك اليوم. لا يُحسب المريض نفسه أيضاً كمتابعة.", period, Coverage.PARTIAL, reason, null, true);
            indicators.unavailable("FOLLOW_UP_PATIENTS", "Follow-up patients", "مرضى المتابعة",
                    "Unique completed-encounter patients whose reasons include follow-up and do not include a first-time visit.",
                    "مرضى الزيارات المكتملة الذين لديهم متابعة وليس لديهم زيارة أولى.", period, Coverage.PARTIAL, reason, null, true);
            indicators.unavailable("CANCELLED_ENCOUNTERS", "Cancelled encounters", "الزيارات الملغاة",
                    "Encounters with status cancelled on the previous facility day.", "زيارات ملغاة في اليوم السابق.", period, Coverage.PARTIAL, reason, null, true);
            indicators.unavailable("INCOMPLETE_ENCOUNTERS", "Incomplete encounters", "الزيارات غير المكتملة",
                    "Encounters on the previous facility day that are neither completed nor cancelled.", "زيارات اليوم السابق التي ليست مكتملة ولا ملغاة.", period, Coverage.PARTIAL, reason, null, true);
            return section(indicators, "ENCOUNTERS", "Encounters and patients", "الزيارات والمرضى",
                    "Encounter counts and patient counts are separate.", "عدد الزيارات وعدد المرضى منفصلان.",
                    List.of(), List.of(indicators.pair("Breakdowns were not produced because the encounter read was incomplete.", "لم تُنتج التفصيلات لأن قراءة الزيارات غير مكتملة.")));
        }

        List<DailyBriefingFacts.EncounterRow> completed = rows.stream().filter(row -> DailyBriefingCalculator.isCompleted(row.status())).toList();
        long cancelled = rows.stream().filter(row -> DailyBriefingCalculator.isCancelled(row.status())).count();
        long incomplete = rows.size() - completed.size() - cancelled;
        DailyBriefingCalculator.PatientSplit split = DailyBriefingCalculator.classifyPatients(completed);
        long newEncounters = completed.stream().filter(row -> row.reason() == EncounterReason.FIRST_TIME_VISIT).count();
        long followEncounters = completed.stream().filter(row -> row.reason() == EncounterReason.FOLLOW_UP).count();
        DailyBriefingFacts.ComparisonCounts comparison = facts.comparison();

        indicators.count("COMPLETED_ENCOUNTERS", "Completed encounters", "الزيارات المكتملة",
                "Encounters with status completed, closed, or discharged. This count is not a patient count.",
                "زيارات بحالة مكتملة أو مغلقة أو خروج. هذا العدد ليس عدد مرضى.",
                period, (long) completed.size(), Coverage.COMPLETE, null,
                comparison(baseline, DailyBriefingCalculator.compare((long) completed.size(), comparison == null ? null : comparison.completedEncounters())),
                Drill.COMPLETED_ENCOUNTERS, true);
        indicators.count("UNIQUE_PATIENTS", "Unique patients", "المرضى الفريدون",
                "Distinct patients among completed encounters. Encounter counts above can be higher.",
                "المرضى المميزون بين الزيارات المكتملة. عدد الزيارات أعلاه قد يكون أكبر.",
                period, split.uniquePatients(), Coverage.COMPLETE, null,
                comparison(baseline, DailyBriefingCalculator.compare(split.uniquePatients(), comparison == null ? null : comparison.uniquePatients())),
                null, true);
        indicators.count("NEW_ENCOUNTERS", "New-patient encounters", "زيارات المرضى الجدد",
                "Completed encounters whose reason is first-time visit. This is an encounter count.",
                "زيارات مكتملة سببها زيارة أولى. هذا عدد زيارات.",
                period, newEncounters, Coverage.COMPLETE, null, null, null, false);
        indicators.count("FOLLOW_UP_ENCOUNTERS", "Follow-up encounters", "زيارات المتابعة",
                "Completed encounters whose reason is follow-up. This is an encounter count.",
                "زيارات مكتملة سببها متابعة. هذا عدد زيارات.",
                period, followEncounters, Coverage.COMPLETE, null, null, null, false);
        indicators.count("NEW_PATIENTS", "New patients", "المرضى الجدد",
                "A unique patient is new when at least one completed encounter that day is a first-time visit. That patient is not also counted as follow-up.",
                "يُعد المريض جديداً إذا كانت له زيارة أولى مكتملة في ذلك اليوم. ولا يُحسب المريض نفسه أيضاً كمتابعة.",
                period, split.newPatients(), Coverage.COMPLETE, null, null, null, true);
        indicators.count("FOLLOW_UP_PATIENTS", "Follow-up patients", "مرضى المتابعة",
                "Unique patients with a follow-up encounter and no first-time visit that day.",
                "مرضى لديهم زيارة متابعة وليس لديهم زيارة أولى في ذلك اليوم.",
                period, split.followUpPatients(), Coverage.COMPLETE, null, null, null, true);
        indicators.count("OTHER_PATIENTS", "Other patients", "مرضى آخرون",
                "Unique completed-encounter patients who are neither new nor follow-up under the rules above.",
                "مرضى الزيارات المكتملة الذين ليسوا جدداً ولا متابعة وفق القواعد أعلاه.",
                period, split.otherPatients(), Coverage.COMPLETE, null, null, null, false);
        indicators.count("CANCELLED_ENCOUNTERS", "Cancelled encounters", "الزيارات الملغاة",
                "Encounters cancelled on the previous facility day. They are not included in completed encounters.",
                "زيارات أُلغيت في اليوم السابق. ليست ضمن الزيارات المكتملة.",
                period, cancelled, Coverage.COMPLETE, null,
                comparison(baseline, DailyBriefingCalculator.compare(cancelled, comparison == null ? null : comparison.cancelledEncounters())),
                Drill.CANCELLED_ENCOUNTERS, true);
        indicators.count("INCOMPLETE_ENCOUNTERS", "Incomplete encounters", "الزيارات غير المكتملة",
                "Previous-day encounters that are neither completed nor cancelled.",
                "زيارات اليوم السابق التي ليست مكتملة ولا ملغاة.",
                period, incomplete, Coverage.COMPLETE, null, null, Drill.INCOMPLETE_ENCOUNTERS, true);

        List<Breakdown> breakdowns = List.of(
                breakdown(indicators, "BY_DEPARTMENT", "By department", "حسب القسم", Coverage.COMPLETE, null, countBy(completed, row -> id(row.departmentId()), row -> name(row.departmentName(), row.departmentId()))),
                breakdown(indicators, "BY_PRACTITIONER", "By practitioner", "حسب الطبيب", Coverage.PARTIAL, indicators.pair("Practitioner names are not stored in this service.", "أسماء الأطباء غير مخزنة في هذه الخدمة."), countBy(completed, row -> id(row.practitionerId()), row -> id(row.practitionerId()))),
                breakdown(indicators, "BY_SERVICE", "By service type", "حسب نوع الخدمة", Coverage.COMPLETE, null, countBy(completed, row -> enumName(row.reason()), row -> enumName(row.reason()))),
                breakdown(indicators, "BY_ENCOUNTER_TYPE", "By encounter type", "حسب نوع الزيارة", Coverage.COMPLETE, null, countBy(completed, row -> enumName(row.encounterType()), row -> enumName(row.encounterType()))),
                new Breakdown("BY_BRANCH", indicators.pair("By branch", "حسب الفرع"), Coverage.NOT_IN_SOURCE, indicators.pair("Branch is not stored on the encounter.", "الفرع غير مخزن على الزيارة."), List.of()),
                new Breakdown("BY_SPECIALTY", indicators.pair("By specialty", "حسب التخصص"), context.specialty() == null ? Coverage.NOT_IN_SOURCE : Coverage.PARTIAL,
                        context.specialty() == null
                                ? indicators.pair("Specialty is stored on the practitioner in the setup service, not on the encounter.", "التخصص مخزن على الطبيب في خدمة الإعداد وليس على الزيارة.")
                                : indicators.pair("The briefing is already limited to the requested specialty.", "الإحاطة محدودة أصلاً بالتخصص المطلوب."),
                        context.specialty() == null ? List.of() : List.of(new DimensionRow(context.specialty(), context.specialty(), completed.size(), null, null)))
        );
        return section(indicators, "ENCOUNTERS", "Encounters and patients", "الزيارات والمرضى",
                "Completed encounter counts are not unique patient counts.", "عدد الزيارات المكتملة ليس عدد المرضى الفريدين.",
                breakdowns, List.of());
    }

    private static Section appointments(BriefingContext context, DailyBriefingFacts facts, Indicators indicators) {
        indicators.begin("APPOINTMENTS");
        String current = currentPeriod(context, indicators);
        String previous = previousPeriod(context, indicators);
        String baseline = comparisonPeriod(context, indicators);
        List<DailyBriefingFacts.StatusCount> today = facts.currentAppointmentStatuses();
        List<DailyBriefingFacts.StatusCount> yesterday = facts.previousAppointmentStatuses();
        if (today == null || yesterday == null || facts.currentOpenSlots() == null) {
            indicators.unavailable("TODAY_APPOINTMENTS", "Today's appointments", "مواعيد اليوم",
                    "Appointments that have a patient.", "مواعيد لها مريض.", current, Coverage.PARTIAL, "QUERY_FAILED", null, true);
            indicators.unavailable("NO_SHOW_RATE", "No-show rate", "نسبة عدم الحضور",
                    "No-shows divided by eligible appointments. Eligible appointments exclude cancellations, rescheduled rows, and empty slots.",
                    "عدم الحضور مقسوماً على المواعيد المؤهلة. تُستبعد الإلغاءات وصفوف إعادة الجدولة والخانات الفارغة.", previous, Coverage.PARTIAL, "QUERY_FAILED", null, true);
            return section(indicators, "APPOINTMENTS", "Appointments and daily readiness", "المواعيد وجاهزية اليوم",
                    "Readiness uses generated schedules and booked appointments.", "الجاهزية تعتمد على الجداول المولدة والمواعيد المحجوزة.", List.of(), List.of());
        }
        long total = DailyBriefingCalculator.sum(today, status -> true);
        long confirmed = DailyBriefingCalculator.sum(today, AppointmentStatus.CONFIRMED.name()::equals);
        long noShow = DailyBriefingCalculator.sum(yesterday, AppointmentStatus.NO_SHOW.name()::equals);
        long cancelled = DailyBriefingCalculator.sum(yesterday, AppointmentStatus.CANCELLED.name()::equals);
        long eligible = DailyBriefingCalculator.sum(yesterday, status -> DailyBriefingCalculator.countsAsEligibleAppointment(safeStatus(status)));
        DailyBriefingCalculator.RateResult rate = DailyBriefingCalculator.rate(noShow, eligible);
        DailyBriefingFacts.ComparisonCounts comparison = facts.comparison();
        Comparison rateComparison = rateComparison(baseline, rate, comparison);
        indicators.count("TODAY_APPOINTMENTS", "Today's appointments", "مواعيد اليوم",
                "Appointments with a patient, including cancelled and no-show rows. Empty schedule slots are not included.",
                "مواعيد لها مريض، بما فيها الملغى وعدم الحضور. خانات الجدول الفارغة غير مشمولة.",
                current, total, context.currentDayPartial() ? Coverage.PARTIAL : Coverage.COMPLETE, null, null, Drill.TODAY_APPOINTMENTS, true);
        indicators.count("CONFIRMED_APPOINTMENTS", "Confirmed appointments", "المواعيد المؤكدة",
                "Appointments whose status is confirmed. Later statuses are not added into this count.",
                "مواعيد حالتها مؤكدة. الحالات اللاحقة لا تُضاف إلى هذا العدد.",
                current, confirmed, context.currentDayPartial() ? Coverage.PARTIAL : Coverage.COMPLETE, null, null, null, true);
        indicators.count("OPEN_SLOTS", "Available slots", "الخانات المتاحة",
                "Schedule rows with no patient and status new. This is remaining capacity on the generated schedule, not a forecast.",
                "صفوف الجدول بلا مريض وبحالة جديد. هذه السعة المتبقية على الجدول المولد وليست توقعاً.",
                current, facts.currentOpenSlots(), context.currentDayPartial() ? Coverage.PARTIAL : Coverage.COMPLETE, null, null, null, true);
        indicators.count("PREVIOUS_CANCELLATIONS", "Previous-day cancellations", "إلغاءات اليوم السابق",
                "Appointments with a patient whose status is cancelled and whose start fell on the previous facility day.",
                "مواعيد لها مريض وحالتها ملغاة وبدايتها في اليوم السابق للمنشأة.",
                previous, cancelled, Coverage.COMPLETE, null, null, Drill.PREVIOUS_CANCELLATIONS, true);
        indicators.rate("NO_SHOW_RATE", "No-show rate", "نسبة عدم الحضور",
                "No-show appointments divided by eligible appointments. Eligible means a patient is attached and the status is not cancelled or rescheduled. Empty slots are excluded. The rate is omitted when the denominator is zero.",
                "مواعيد عدم الحضور مقسومة على المواعيد المؤهلة. المؤهل يعني وجود مريض وأن الحالة ليست ملغاة أو معاد جدولتها. الخانات الفارغة مستبعدة. تُحذف النسبة إذا كان المقام صفراً.",
                previous, rate, noShow, eligible, Coverage.COMPLETE, null, rateComparison, true);

        List<String> notes = new ArrayList<>();
        notes.add(indicators.pair("Session buckets are clock ranges in the facility time zone. A shift master is not stored.", "فترات الجلسة هي نطاقات ساعة بتوقيت المنشأة. لا يوجد جدول ورديات مخزن."));
        if (facts.currentBookedCapped() || facts.currentBooked() == null) {
            indicators.unavailable("SCHEDULE_CONFLICTS", "Schedule conflicts", "تعارضات الجدول",
                    "Overlapping booked appointments for the same practitioner.", "مواعيد محجوزة متداخلة لنفس الطبيب.", current, Coverage.PARTIAL,
                    facts.currentBookedCapped() ? "VOLUME_CAP" : "QUERY_FAILED", null, false);
            indicators.unavailable("OBSERVED_PEAK_HOURS", "Observed peak hours", "ساعات الذروة المرصودة",
                    "Hours with the most booked appointments today. This is not a forecast.", "الساعات الأكثر حجوزات اليوم. هذا ليس توقعاً.", current, Coverage.PARTIAL,
                    facts.currentBookedCapped() ? "VOLUME_CAP" : "QUERY_FAILED", null, false);
        } else {
            List<DailyBriefingCalculator.Conflict> conflicts = DailyBriefingCalculator.conflicts(facts.currentBooked());
            indicators.count("SCHEDULE_CONFLICTS", "Schedule conflicts", "تعارضات الجدول",
                    "Pairs of booked appointments for the same practitioner whose times overlap. Touching start and end is not an overlap. Appointments with no resolvable practitioner are excluded.",
                    "أزواج مواعيد محجوزة لنفس الطبيب تتداخل أوقاتها. تماس النهاية مع البداية ليس تداخلاً. المواعيد بلا طبيب محدد مستبعدة.",
                    current, (long) conflicts.size(), context.currentDayPartial() ? Coverage.PARTIAL : Coverage.COMPLETE, null, null, Drill.SCHEDULE_CONFLICTS, false);
            List<Instant> starts = facts.currentBooked().stream()
                    .filter(row -> DailyBriefingCalculator.countsAsEligibleAppointment(row.status()))
                    .map(DailyBriefingFacts.AppointmentRow::start)
                    .toList();
            List<DimensionRow> sessions = sessionRows(starts, context.zone());
            List<DimensionRow> peaks = DailyBriefingCalculator.busiestHours(starts, context.zone(), 3).stream()
                    .map(hour -> new DimensionRow(String.valueOf(hour.hour()), hour.hour() + ":00", hour.count(), null, null))
                    .toList();
            notes.add(indicators.pair("Peak hours are observed booking counts, not an expected-load forecast.", "ساعات الذروة هي أعداد حجز مرصودة وليست توقع حمل."));
            return appointmentSection(context, facts, indicators, notes, sessions, peaks, current);
        }
        return appointmentSection(context, facts, indicators, notes, List.of(), List.of(), current);
    }

    private static Section appointmentSection(BriefingContext context, DailyBriefingFacts facts, Indicators indicators, List<String> notes,
                                               List<DimensionRow> sessions, List<DimensionRow> peaks, String current) {
        indicators.unavailable("EXPECTED_PEAK", "Expected peak load", "ذروة الحمل المتوقعة",
                "A forecast of peak load.", "توقع لذروة الحمل.", current, Coverage.NOT_IN_SOURCE, "NO_FORECAST_STORED",
                indicators.pair("No forecast model is stored.", "لا يوجد نموذج توقع مخزن."), false);
        indicators.unavailable("RESOURCE_UNAVAILABLE_BOOKINGS", "Bookings without the required resource", "حجوزات دون المورد المطلوب",
                "Bookings made although the practitioner or required resource is unavailable.", "حجوزات رغم عدم توفر الطبيب أو المورد.", current, Coverage.NOT_IN_SOURCE, "AVAILABILITY_NOT_STORED",
                indicators.pair("Practitioner leave and resource closure are not stored in this service. Bookings with no practitioner id are reported separately when that flag is set.", "إجازات الأطباء وإغلاق الموارد غير مخزنة في هذه الخدمة. الحجوزات بلا معرف طبيب تُعرض منفصلة عندما تكون العلامة مفعّلة."), false);
        Long missingPractitioner = facts.bookedWithoutPractitioner();
        indicators.count("BOOKED_WITHOUT_PRACTITIONER", "Booked without a practitioner", "محجوز بلا طبيب",
                "Booked appointments that require a practitioner but have neither a default practitioner nor a practitioner resource.",
                "مواعيد محجوزة تتطلب طبيباً وليس لها طبيب افتراضي ولا مورد طبيب.",
                current, missingPractitioner, context.currentDayPartial() ? Coverage.PARTIAL : Coverage.COMPLETE, null, null, Drill.BOOKED_WITHOUT_PRACTITIONER, false);
        List<Breakdown> breakdowns = List.of(
                breakdown(indicators, "BY_DEPARTMENT", "By clinic", "حسب العيادة", facts.currentAppointmentsByDepartment() == null ? Coverage.PARTIAL : Coverage.COMPLETE,
                        facts.currentAppointmentsByDepartment() == null ? "QUERY_FAILED" : null,
                        departmentRows(facts.currentAppointmentsByDepartment())),
                breakdown(indicators, "BY_SERVICE", "By service type", "حسب نوع الخدمة", facts.currentAppointmentsByService() == null ? Coverage.PARTIAL : Coverage.COMPLETE,
                        facts.currentAppointmentsByService() == null ? "QUERY_FAILED" : null,
                        facts.currentAppointmentsByService() == null ? List.of() : facts.currentAppointmentsByService().stream().map(row -> new DimensionRow(row.status(), row.status(), row.count(), null, null)).toList()),
                breakdown(indicators, "BY_SESSION", "By derived session", "حسب الجلسة المشتقة", facts.currentBooked() == null ? Coverage.PARTIAL : Coverage.PARTIAL,
                        indicators.pair("Derived from the facility clock because a shift master is not stored.", "مشتقة من ساعة المنشأة لأن جدول الورديات غير مخزن."), sessions),
                breakdown(indicators, "OBSERVED_PEAK_HOURS", "Observed busiest hours", "أكثر الساعات حجزاً", facts.currentBooked() == null ? Coverage.PARTIAL : Coverage.COMPLETE, null, peaks)
        );
        return section(indicators, "APPOINTMENTS", "Appointments and daily readiness", "المواعيد وجاهزية اليوم",
                "Confirmed appointments, open slots, cancellations, and the no-show rate use separate definitions and are not added together.",
                "المواعيد المؤكدة والخانات المتاحة والإلغاءات ونسبة عدم الحضور تعريفات منفصلة ولا تُجمع معاً.",
                breakdowns, notes);
    }

    private static Section billing(BriefingContext context, DailyBriefingFacts facts, Indicators indicators) {
        indicators.begin("BILLING");
        String period = previousPeriod(context, indicators);
        DailyBriefingFacts.BillingSnapshot billing = facts.billing() == null
                ? new DailyBriefingFacts.BillingSnapshot(null, null, null, null, null, null, null, null, null, null, null, null)
                : facts.billing();
        String definition = "Net billing = gross - discount - exemption - credit notes, for active charges dated on the previous facility day and credit notes issued that day, by currency. Cancellations are shown separately and are not subtracted again. Collections are not part of net billing. An issued invoice is not collected revenue.";
        String definitionAr = "صافي الفوترة = الإجمالي - الخصم - الإعفاء - الإشعارات الدائنة، للرسوم النشطة بتاريخ اليوم السابق والإشعارات الدائنة الصادرة ذلك اليوم، لكل عملة. الإلغاءات معروضة منفصلة ولا تُطرح مرة أخرى. التحصيل ليس جزءاً من صافي الفوترة. الفاتورة الصادرة ليست إيراداً محصلاً.";
        if (false) {
            indicators.unavailable("GROSS_BILLING", "Gross billing", "إجمالي الفوترة", definition, definitionAr, period, Coverage.PARTIAL, "QUERY_FAILED", null, true);
            indicators.unavailable("NET_BILLING", "Net billing", "صافي الفوترة", definition, definitionAr, period, Coverage.PARTIAL, "QUERY_FAILED", null, true);
            indicators.unavailable("PATIENT_COLLECTIONS", "Patient payments", "مدفوعات المريض", "Posted patient payments.", "مدفوعات المريض المرحلة.", period, Coverage.PARTIAL, "QUERY_FAILED", null, true);
            indicators.unavailable("ISSUED_INVOICES", "Issued invoices", "الفواتير الصادرة", "Issued invoice totals are not collections.", "مجاميع الفواتير الصادرة ليست تحصيلاً.", period, Coverage.PARTIAL, "QUERY_FAILED", null, true);
            return section(indicators, "BILLING", "Billing and collections", "الفوترة والتحصيل", definition, definitionAr, List.of(), List.of());
        }
        Coverage coverage = (billing.debitNotes() != null && !billing.debitNotes().isEmpty()) || (billing.reversedWithoutTimestamp() != null && billing.reversedWithoutTimestamp() > 0)
                ? Coverage.PARTIAL : Coverage.COMPLETE;
        String note = coverage == Coverage.PARTIAL
                ? indicators.pair("Debit notes or reversed charges without a timestamp are disclosed and are not forced into the net formula.", "الإشعارات المدينة أو الرسوم المعكوسة بلا طابع زمني معروضة ولا تُقحم في معادلة الصافي.")
                : null;
        indicators.amount("GROSS_BILLING", "Gross billing", "إجمالي الفوترة", definition, definitionAr, period, money(billing.activeCharges(), ChargePart.GROSS), coverage, note, null, true);
        indicators.amount("DISCOUNTS", "Discounts", "الخصومات", "Discounts on active charges.", "خصومات الرسوم النشطة.", period, money(billing.activeCharges(), ChargePart.DISCOUNT), coverage, null, null, false);
        indicators.amount("EXEMPTIONS", "Exemptions", "الإعفاءات", "Exemptions on active charges.", "إعفاءات الرسوم النشطة.", period, money(billing.activeCharges(), ChargePart.EXEMPTION), coverage, null, null, false);
        indicators.amount("CANCELLATIONS", "Charge cancellations", "إلغاءات الرسوم", "Net amount of charges cancelled on the previous day. Not subtracted again from net billing.", "صافي الرسوم الملغاة في اليوم السابق. لا يُطرح مرة أخرى من صافي الفوترة.", period, moneyRows(billing.cancellations()), coverage, null, null, false);
        indicators.amount("CREDIT_NOTES", "Credit notes", "الإشعارات الدائنة", "Credit notes issued on the previous day.", "إشعارات دائنة صدرت في اليوم السابق.", period, moneyRows(billing.creditNotes()), coverage, null, null, false);
        indicators.amount("NET_BILLING", "Net billing", "صافي الفوترة", definition, definitionAr, period, net(billing), coverage, note, null, true);
        indicators.amount("PATIENT_COLLECTIONS", "Posted patient payments", "مدفوعات المريض المرحلة",
                "Sum of posted rows in patient payments, once per payment. Not added to billing-payment collections. Net of refunds is not calculated for this source because a daily refund movement is not stored there. An issued invoice is not included.",
                "مجموع صفوف مدفوعات المريض المرحلة، مرة لكل دفعة. لا يُضاف إلى تحصيل مدفوعات الفوترة. لا يُحسب الصافي بعد الاسترداد لهذا المصدر لأن حركة الاسترداد اليومية غير مخزنة هناك. الفاتورة الصادرة غير مشمولة.",
                period, moneyRows(billing.postedPatientPayments()), Coverage.PARTIAL,
                indicators.pair("Unposted payments are excluded. Refunds for this source are not subtracted.", "المدفوعات غير المرحلة مستبعدة. لا تُطرح استردادات هذا المصدر."),
                null, true);
        indicators.amount("BILLING_PAYMENT_COLLECTIONS", "Billing payments", "مدفوعات الفوترة",
                "Completed billing payments, once per payment, split by payer type. Not added to posted patient payments.",
                "مدفوعات الفوترة المكتملة، مرة لكل دفعة، مفصولة حسب نوع الدافع. لا تُضاف إلى مدفوعات المريض المرحلة.",
                period, moneyRows(billing.billingPayments()), Coverage.PARTIAL,
                indicators.pair("Payments that are not linked to an encounter are excluded because their facility cannot be determined.", "المدفوعات غير المرتبطة بزيارة مستبعدة لأن منشأتها لا تُعرف."),
                null, true);
        indicators.amount("BILLING_REFUNDS", "Billing refunds", "استردادات الفوترة",
                "Completed refund amounts linked to an original billing payment.", "مبالغ الاسترداد المكتملة المرتبطة بدفعة فوترة أصلية.",
                period, moneyRows(billing.classifiedRefunds()), billing.unclassifiedRefunds() != null && billing.unclassifiedRefunds() > 0 ? Coverage.PARTIAL : Coverage.COMPLETE,
                null, null, false);
        indicators.amount("PATIENT_RESPONSIBILITY", "Patient responsibility", "مسؤولية المريض",
                "Responsibility amounts whose party is the patient. Not treated as collected revenue.", "مبالغ المسؤولية التي طرفها المريض. لا تُعامل كإيراد محصل.",
                period, labeled(billing.responsibilities(), "PATIENT"), Coverage.COMPLETE, null, null, false);
        indicators.amount("PAYER_RESPONSIBILITY", "Payer responsibility", "مسؤولية الدافع",
                "Responsibility amounts whose party is not the patient. Not added to patient responsibility and not treated as collected.", "مبالغ المسؤولية التي طرفها ليس المريض. لا تُضاف إلى مسؤولية المريض ولا تُعامل كمحصل.",
                period, labeledExcept(billing.responsibilities(), "PATIENT"), Coverage.COMPLETE, null, null, false);
        indicators.amount("ISSUED_INVOICES", "Issued invoices", "الفواتير الصادرة",
                "Invoice documents issued on the previous day. This is not collected revenue.", "مستندات فواتير صدرت في اليوم السابق. هذا ليس إيراداً محصلاً.",
                period, moneyRows(billing.issuedInvoices()), Coverage.COMPLETE,
                indicators.pair("An issued invoice is not counted as a collection.", "الفاتورة الصادرة لا تُحسب تحصيلاً."), null, true);
        List<String> notes = new ArrayList<>();
        notes.add(indicators.pair("Patient payments and billing payments are separate sources and are not added together.", "مدفوعات المريض ومدفوعات الفوترة مصدران منفصلان ولا يُجمعان."));
        if (billing.unpostedPatientPayments() != null && billing.unpostedPatientPayments() > 0) {
            notes.add(indicators.pair(billing.unpostedPatientPayments() + " patient payments are still unposted and are excluded from collections.", billing.unpostedPatientPayments() + " مدفوعات مريض لم تُرحّل وهي مستبعدة من التحصيل."));
        }
        return section(indicators, "BILLING", "Billing and collections", "الفوترة والتحصيل", definition, definitionAr, List.of(), notes);
    }

    private static Section insurance(BriefingContext context, DailyBriefingFacts facts, Indicators indicators) {
        indicators.begin("INSURANCE");
        String open = indicators.pair("Open pipeline as of " + context.refreshedAt(), "خط مفتوح حتى " + context.refreshedAt());
        String previous = previousPeriod(context, indicators);
        if (facts.authorizations() == null || facts.claims() == null) {
            indicators.unavailable("PENDING_AUTHORIZATIONS", "Pending authorizations", "موافقات معلقة", "Prior authorizations that are not approved, denied, or closed.", "موافقات مسبقة ليست موافق عليها ولا مرفوضة ولا مغلقة.", open, Coverage.PARTIAL, "QUERY_FAILED", null, true);
            indicators.unavailable("CLAIM_DENIALS", "Claim denials", "رفض المطالبات", "Claims whose current status is rejected. Distinct from authorization denials.", "مطالبات حالتها الحالية مرفوضة. وهي غير رفض الموافقات.", open, Coverage.PARTIAL, "QUERY_FAILED", null, true);
            return section(indicators, "INSURANCE", "Insurance, authorizations, and claims", "التأمين والموافقات والمطالبات",
                    "Authorization amounts and claim amounts are not added together.", "مبالغ الموافقات ومبالغ المطالبات لا تُجمع معاً.", List.of(), List.of());
        }
        long pending = 0;
        long unclassified = 0;
        long denials = 0;
        long aged = 0;
        Map<String, BigDecimal> pendingMoney = new LinkedHashMap<>();
        Map<String, Long> aging = new LinkedHashMap<>();
        Map<String, Long> denialByPayor = new LinkedHashMap<>();
        for (DailyBriefingFacts.AuthRow row : facts.authorizations()) {
            if (DailyBriefingCalculator.isAuthorizationDenial(row.status(), row.outcome())) {
                denials += row.count();
                denialByPayor.merge(row.payorId() == null ? "UNKNOWN" : row.payorId().toString(), row.count(), Long::sum);
            } else if (DailyBriefingCalculator.isClassifiedPendingAuthorization(row.status(), row.outcome())) {
                pending += row.count();
                String bucket = DailyBriefingCalculator.agingBucket(row.orderedOn(), context.currentDay());
                aging.merge(bucket, row.count(), Long::sum);
                if ("15_PLUS".equals(bucket)) {
                    aged += row.count();
                }
                pendingMoney.merge("REQUESTED", zero(row.requestedNet()), BigDecimal::add);
            } else if (DailyBriefingCalculator.isUnclassifiedOpenAuthorization(row.status(), row.outcome())) {
                unclassified += row.count();
                pending += row.count();
            }
        }
        Coverage authCoverage = unclassified > 0 ? Coverage.PARTIAL : Coverage.COMPLETE;
        String pendingNote = indicators.pair(
                "Requested net " + pendingMoney.getOrDefault("REQUESTED", BigDecimal.ZERO) + " is not an approved amount.",
                "صافي المطلوب " + pendingMoney.getOrDefault("REQUESTED", BigDecimal.ZERO) + " ليس مبلغاً معتمداً.");
        if (unclassified > 0) {
            pendingNote = pendingNote + " " + indicators.pair(unclassified + " rows have a status outside the classified pending list and are included so they are not hidden.", unclassified + " صفوف لها حالة خارج قائمة المعلق المصنف وهي مشمولة حتى لا تُخفى.");
        }
        indicators.count("PENDING_AUTHORIZATIONS", "Pending authorizations", "موافقات معلقة",
                "Prior authorizations that are not approved, partially approved, cancelled, not required, or denied. Requested net is not an approved amount.",
                "موافقات مسبقة ليست موافق عليها ولا موافقاً عليها جزئياً ولا ملغاة ولا غير مطلوبة ولا مرفوضة. صافي الطلب ليس مبلغاً معتمداً.",
                open, pending, authCoverage, pendingNote,
                null, Drill.PENDING_AUTHORIZATIONS, true);
        indicators.count("AUTH_AGING_15_PLUS", "Authorizations aged 15 days or more", "موافقات عمرها 15 يوماً أو أكثر",
                "Classified pending authorizations whose order date is at least 15 days before the briefing day. Unknown order dates are not included in this bucket.",
                "موافقات معلقة مصنفة تاريخ طلبها قبل يوم الإحاطة بـ 15 يوماً على الأقل. التواريخ غير المعروفة ليست في هذه الفئة.",
                open, aged, authCoverage, null, null, null, false);
        indicators.count("AUTHORIZATION_DENIALS", "Authorization denials", "رفض الموافقات",
                "Prior authorizations whose status or outcome is rejected or denied. These are not claim denials.",
                "موافقات مسبقة حالتها أو نتيجتها مرفوضة. هذه ليست رفض مطالبات.",
                open, denials, Coverage.COMPLETE, null, null, Drill.AUTHORIZATION_DENIALS, true);
        indicators.count("SERVICES_ON_HOLD", "Services on hold", "خدمات موقوفة",
                "Charge responsibilities that require prior authorization and are pending approval. Their amounts are not added to authorization or claim totals.",
                "مسؤوليات رسوم تتطلب موافقة مسبقة وهي بانتظار الموافقة. مبالغها لا تُضاف إلى مجاميع الموافقات أو المطالبات.",
                open, facts.servicesOnHoldCount(), facts.servicesOnHold() == null ? Coverage.PARTIAL : Coverage.COMPLETE, null, null, Drill.SERVICES_ON_HOLD, true);

        long ready = claimCount(facts.claims(), "DRAFT");
        long rejected = claimCount(facts.claims(), "REJECTED");
        long failed = claimCount(facts.claims(), "FAILED");
        indicators.count("READY_CLAIMS", "Ready claims not submitted", "مطالبات جاهزة غير مرسلة",
                "Claims whose current status is draft.", "مطالبات حالتها الحالية مسودة.", open, ready, Coverage.COMPLETE, null, null, Drill.READY_CLAIMS, true);
        indicators.count("CLAIM_DENIALS", "Claim denials", "رفض المطالبات",
                "Claims whose current status is rejected. Distinct from authorization denials. The amount is the claim net, not an approved or collected amount.",
                "مطالبات حالتها الحالية مرفوضة. وهي غير رفض الموافقات. المبلغ هو صافي المطالبة وليس مبلغاً معتمداً أو محصلاً.",
                open, rejected, Coverage.COMPLETE, null, null, Drill.CLAIM_DENIALS, true);
        indicators.count("CLAIM_FAILURES", "Claim submission failures", "فشل إرسال المطالبات",
                "Claims whose current status is failed. The system has no separate returned-for-correction status, so this is not labeled as a payer return.",
                "مطالبات حالتها الحالية فشل. لا توجد حالة منفصلة للإعادة للتصحيح، لذلك لا تُسمى إعادة من الدافع.",
                open, failed, Coverage.PARTIAL, indicators.pair("Failed is the closest stored status. It is not a recorded return-for-correction reason.", "الفشل هو أقرب حالة مخزنة. وهو ليس سبب إعادة للتصحيح مسجلاً."), null, Drill.CLAIM_FAILURES, false);
        indicators.amount("CLAIMED_AMOUNT", "Claimed amount", "المبلغ المطالب به",
                "Sum of claim net for claims submitted on the previous facility day, once per claim. Not the sum of claim items, and not added to authorization amounts.",
                "مجموع صافي المطالبات المرسلة في اليوم السابق للمنشأة، مرة لكل مطالبة. ليس مجموع بنود المطالبة، ولا يُضاف إلى مبالغ الموافقات.",
                previous, claimMoney(facts.claimsSubmittedYesterday()), facts.claimsSubmittedYesterday() == null ? Coverage.PARTIAL : Coverage.COMPLETE, null, null, true);
        indicators.unavailable("APPROVED_CLAIM_AMOUNT", "Approved claim amount", "المبلغ المعتمد للمطالبة",
                "Payer approved amount.", "المبلغ الذي اعتمده الدافع.", open, Coverage.NOT_IN_SOURCE, "APPROVED_AMOUNT_NOT_STORED",
                indicators.pair("Claim net is not used as the approved amount.", "صافي المطالبة لا يُستخدم كمبلغ معتمد."), true);
        indicators.unavailable("COLLECTED_CLAIM_AMOUNT", "Collected claim amount", "المبلغ المحصل للمطالبة",
                "Amount collected against the claim.", "المبلغ المحصل مقابل المطالبة.", open, Coverage.NOT_IN_SOURCE, "CLAIM_COLLECTION_NOT_LINKED",
                indicators.pair("Payer remittance is not linked to the claim, so collections are not attributed to claims.", "تحويل الدافع غير مرتبط بالمطالبة، لذلك لا يُنسب التحصيل إلى المطالبات."), true);
        indicators.unavailable("SUBMISSION_DEADLINES", "Submission deadlines", "مواعيد التقديم",
                "Upcoming claim or authorization deadlines.", "مواعيد تقديم أو إعادة تقديم قادمة.", open, Coverage.NOT_IN_SOURCE, "DEADLINE_NOT_STORED", null, false);

        List<Breakdown> breakdowns = List.of(
                new Breakdown("AUTH_AGING", indicators.pair("Authorization aging", "تقادم الموافقات"), authCoverage, null, aging.entrySet().stream().map(entry -> new DimensionRow(entry.getKey(), entry.getKey(), entry.getValue(), null, null)).toList()),
                new Breakdown("AUTH_DENIALS_BY_PAYOR", indicators.pair("Authorization denials by payer", "رفض الموافقات حسب الدافع"), Coverage.PARTIAL, indicators.pair("Payer name is not stored on the authorization; the payer id is used. Unknown means no payer id.", "اسم الدافع غير مخزن على الموافقة؛ يُستخدم معرف الدافع. غير معروف يعني عدم وجود معرف."), denialByPayor.entrySet().stream().map(entry -> new DimensionRow(entry.getKey(), entry.getKey(), entry.getValue(), null, null)).toList()),
                new Breakdown("CLAIM_DENIALS_BY_REASON", indicators.pair("Claim denials by outcome", "رفض المطالبات حسب النتيجة"), Coverage.COMPLETE, indicators.pair("Grouped by the stored outcome. The claim message is not copied because it may contain clinical text.", "مجمعة حسب النتيجة المخزنة. لا تُنسخ رسالة المطالبة لأنها قد تحتوي نصاً سريرياً."), claimReasons(facts.claims()))
        );
        return section(indicators, "INSURANCE", "Insurance, authorizations, and claims", "التأمين والموافقات والمطالبات",
                "Authorization denials, claim denials, requested amounts, claimed amounts, and collections are separate. They are not added together.",
                "رفض الموافقات ورفض المطالبات والمبالغ المطلوبة والمبالغ المطالب بها والتحصيل كلها منفصلة. لا تُجمع معاً.",
                breakdowns, List.of(indicators.pair("Pending requested net is a single requested-currency total only when every row shares that figure's currency field; rows here are summed as requested net without a currency column on the authorization.", "صافي الطلب للمعلقات يُجمع كصافي مطلوب. مستند الموافقة لا يحمل عملة في هذا التجميع.")));
    }

    private static Section waiting(BriefingContext context, DailyBriefingFacts facts, Indicators indicators) {
        indicators.begin("WAITING");
        String previous = previousPeriod(context, indicators);
        String open = indicators.pair("Open as of " + context.refreshedAt(), "مفتوح حتى " + context.refreshedAt());
        if (facts.previousEncounters() == null || facts.previousEncountersCapped()) {
            indicators.unavailable("WAITING_TIME", "Arrival to service start", "من الوصول إلى بدء الخدمة",
                    "Minutes from appointment check-in to encounter start.", "الدقائق من تسجيل الوصول إلى بدء الزيارة.", previous, Coverage.PARTIAL, facts.previousEncountersCapped() ? "VOLUME_CAP" : "QUERY_FAILED", null, true);
        } else {
            List<Long> waits = new ArrayList<>();
            long missing = 0;
            long over = 0;
            for (DailyBriefingFacts.EncounterRow row : facts.previousEncounters()) {
                Long minutes = DailyBriefingCalculator.waitMinutes(row.checkedInAt(), row.startedDate());
                if (minutes == null) {
                    missing++;
                } else {
                    waits.add(minutes);
                    if (minutes > context.waitingMinutes()) {
                        over++;
                    }
                }
            }
            BigDecimal median = DailyBriefingCalculator.median(waits);
            Coverage coverage = missing > 0 ? Coverage.PARTIAL : Coverage.COMPLETE;
            indicators.count("WAITING_TIME_OVER_TARGET", "Waits over the highlight threshold", "انتظار فوق حد التنبيه",
                    "Encounters whose check-in-to-start wait exceeds the highlight threshold. The threshold is not an approved clinical target.",
                    "زيارات تجاوز انتظارها من تسجيل الوصول إلى البدء حد التنبيه. الحد ليس هدفاً سريرياً معتمداً.",
                    previous, over, coverage, null, null, null, false);
            indicators.count("MEASURED_WAITS", "Measured waits", "حالات انتظار مقيسة",
                    "Encounters with both a check-in time and a start time. Missing timestamps are excluded and are not treated as zero. The median is in the note, in minutes.",
                    "زيارات لها وقت تسجيل وصول ووقت بدء. الطوابع الناقصة تُستبعد ولا تُعامل كصفر. الوسيط مذكور في الملاحظة بالدقائق.",
                    previous, (long) waits.size(), coverage,
                    indicators.pair("Median minutes: " + (median == null ? "not calculated" : median.toPlainString()) + ". Excluded: " + missing + ".",
                            "الوسيط بالدقائق: " + (median == null ? "غير محسوب" : median.toPlainString()) + ". المستبعد: " + missing + "."),
                    null, null, true);
        }
        orderTurnaround(context, facts, indicators, previous, open);
        indicators.unavailable("CRITICAL_RESULTS", "Unacknowledged critical results", "نتائج حرجة غير مؤكدة الاستلام",
                "Critical results whose receipt or handling has not been acknowledged.",
                "نتائج حرجة لم يُؤكد استلامها أو التعامل معها.",
                open, Coverage.NOT_IN_SOURCE, "ACKNOWLEDGEMENT_NOT_RECORDED",
                indicators.pair("Critical markers and critical-findings text exist, and reports have a generic review time. That review time is not a recorded acknowledgment of a critical result, so no unacknowledged list is shown. This briefing does not replace the facility's real-time critical-result process.",
                        "توجد علامات حرجة ونص نتائج حرجة، وللتقارير وقت مراجعة عام. وقت المراجعة هذا ليس تأكيداً مسجلاً لاستلام نتيجة حرجة، لذلك لا تُعرض قائمة غير مؤكدة. هذه الإحاطة لا تغني عن إجراء النتائج الحرجة الفوري في المنشأة."),
                true);
        return section(indicators, "WAITING", "Waiting times, delays, and results", "أوقات الانتظار والتأخير والنتائج",
                "Waiting time and turnaround use stored timestamps only.", "وقت الانتظار وزمن الإنجاز يعتمدان على الطوابع المخزنة فقط.",
                List.of(), List.of(indicators.pair("The highlight thresholds are not approved targets.", "حدود التنبيه ليست أهدافاً معتمدة.")));
    }

    private static void orderTurnaround(BriefingContext context, DailyBriefingFacts facts, Indicators indicators, String previous, String open) {
        DailyBriefingFacts.OrderSnapshot orders = facts.orders();
        if (orders == null || orders.overdueOrders() == null) {
            indicators.unavailable("OVERDUE_ORDERS", "Orders over the turnaround highlight", "طلبات تجاوزت حد الإنجاز",
                    "Open diagnostic orders older than the highlight threshold.", "طلبات تشخيص مفتوحة أقدم من حد التنبيه.", open, Coverage.PARTIAL, "QUERY_FAILED", null, true);
            indicators.unavailable("LAB_TURNAROUND", "Laboratory turnaround", "زمن إنجاز المختبر", "Median minutes for completed laboratory orders.", "وسيط الدقائق لطلبات المختبر المكتملة.", previous, Coverage.PARTIAL, "QUERY_FAILED", null, true);
            indicators.unavailable("RADIOLOGY_TURNAROUND", "Radiology turnaround", "زمن إنجاز الأشعة", "Median minutes for completed radiology orders.", "وسيط الدقائق لطلبات الأشعة المكتملة.", previous, Coverage.PARTIAL, "QUERY_FAILED", null, true);
            return;
        }
        indicators.count("OVERDUE_ORDERS", "Orders over the turnaround highlight", "طلبات تجاوزت حد الإنجاز",
                "Open laboratory, radiology, pathology, or microbiology orders whose age exceeds the highlight threshold. Orders still inside that threshold are counted separately and are not called overdue.",
                "طلبات مختبر أو أشعة أو أمراض أو أحياء دقيقة مفتوحة تجاوز عمرها حد التنبيه. الطلبات داخل الحد تُحسب منفصلة ولا تُسمى متأخرة.",
                open, orders.overdueOrders(), orders.ordersMissingAge() != null && orders.ordersMissingAge() > 0 ? Coverage.PARTIAL : Coverage.COMPLETE, null, null, Drill.OVERDUE_ORDERS, true);
        indicators.count("ORDERS_WITHIN_CYCLE", "Orders still within the highlight window", "طلبات ضمن نافذة التنبيه",
                "Open diagnostic orders younger than the highlight threshold.", "طلبات تشخيص مفتوحة أصغر من حد التنبيه.",
                open, orders.withinCycleOrders(), Coverage.COMPLETE, null, null, null, false);
        if (orders.turnaroundCapped() || orders.turnaround() == null) {
            indicators.unavailable("LAB_TURNAROUND", "Laboratory turnaround", "زمن إنجاز المختبر", "Median minutes.", "وسيط الدقائق.", previous, Coverage.PARTIAL, "VOLUME_CAP", null, true);
            indicators.unavailable("RADIOLOGY_TURNAROUND", "Radiology turnaround", "زمن إنجاز الأشعة", "Median minutes.", "وسيط الدقائق.", previous, Coverage.PARTIAL, "VOLUME_CAP", null, true);
            return;
        }
        medianOrder(indicators, "LAB_TURNAROUND", "Laboratory turnaround", "زمن إنجاز المختبر", previous, orders, "LABORATORY");
        medianOrder(indicators, "PATHOLOGY_TURNAROUND", "Pathology turnaround", "زمن إنجاز الأمراض", previous, orders, "PATHOLOGY");
        medianOrder(indicators, "MICROBIOLOGY_TURNAROUND", "Microbiology turnaround", "زمن إنجاز الأحياء الدقيقة", previous, orders, "MICROBIOLOGY");
        medianOrder(indicators, "RADIOLOGY_TURNAROUND", "Radiology turnaround", "زمن إنجاز الأشعة", previous, orders, "RADIOLOGY");
    }

    private static void medianOrder(Indicators indicators, String code, String en, String ar, String period, DailyBriefingFacts.OrderSnapshot orders, String type) {
        List<Long> minutes = orders.turnaround().stream().filter(sample -> type.equals(sample.orderType())).map(DailyBriefingFacts.TatSample::minutes).toList();
        BigDecimal median = DailyBriefingCalculator.median(minutes);
        indicators.count(code, en, ar,
                "Median minutes from arrival, otherwise submission, to approval or ready time for " + type + " orders completed on the previous day. Orders without a start timestamp are excluded.",
                "وسيط الدقائق من الوصول، وإلا من الإرسال، إلى الاعتماد أو الجاهزية لطلبات " + type + " المكتملة في اليوم السابق. الطلبات بلا طابع بداية مستبعدة.",
                period, (long) minutes.size(), orders.turnaroundMissingStart() > 0 ? Coverage.PARTIAL : Coverage.COMPLETE,
                indicators.pair("Median minutes: " + (median == null ? "not calculated" : median.toPlainString()) + ".", "الوسيط بالدقائق: " + (median == null ? "غير محسوب" : median.toPlainString()) + "."),
                null, null, "LAB_TURNAROUND".equals(code) || "RADIOLOGY_TURNAROUND".equals(code));
    }

    private static Section completion(BriefingContext context, DailyBriefingFacts facts, Indicators indicators) {
        indicators.begin("COMPLETION");
        String open = indicators.pair("Open as of " + context.refreshedAt() + ". Highlight threshold " + context.openEncounterHours() + " hours.", "مفتوح حتى " + context.refreshedAt() + ". حد التنبيه " + context.openEncounterHours() + " ساعة.");
        String previous = previousPeriod(context, indicators);
        if (facts.openEncountersCapped() || facts.openEncounters() == null) {
            indicators.count("OPEN_ENCOUNTER_BACKLOG", "Open encounter rows", "صفوف الزيارات المفتوحة",
                    "Encounters that are not completed, closed, discharged, or cancelled. Age was not split because the read was incomplete.",
                    "زيارات ليست مكتملة أو مغلقة أو خروجاً أو ملغاة. لم يُقسم العمر لأن القراءة غير مكتملة.",
                    open, facts.openEncounterTotal(), Coverage.PARTIAL, null, null, null, false);
            indicators.unavailable("OPEN_ENCOUNTERS", "Encounters open beyond the highlight", "زيارات مفتوحة بعد حد التنبيه",
                    "Open encounters older than the highlight threshold.", "زيارات مفتوحة أقدم من حد التنبيه.", open, Coverage.PARTIAL, facts.openEncountersCapped() ? "VOLUME_CAP" : "QUERY_FAILED", null, true);
        } else {
            long overdue = 0;
            long within = 0;
            long unknown = 0;
            for (DailyBriefingFacts.EncounterRow row : facts.openEncounters()) {
                Long age = DailyBriefingCalculator.ageMinutes(row.startedDate(), row.encounterDate(), row.encounterTime(), context.zone(), context.refreshedAt());
                if (age == null) {
                    unknown++;
                } else if (age > context.openEncounterHours() * 60L) {
                    overdue++;
                } else {
                    within++;
                }
            }
            indicators.count("OPEN_ENCOUNTERS", "Encounters open beyond the highlight", "زيارات مفتوحة بعد حد التنبيه",
                    "Open encounters whose age exceeds the highlight threshold. This threshold is not an approved target.",
                    "زيارات مفتوحة تجاوز عمرها حد التنبيه. هذا الحد ليس هدفاً معتمداً.",
                    open, overdue, unknown > 0 ? Coverage.PARTIAL : Coverage.COMPLETE, null, null, Drill.OPEN_ENCOUNTERS, true);
            indicators.count("ENCOUNTERS_WITHIN_CYCLE", "Open encounters within the highlight window", "زيارات مفتوحة ضمن نافذة التنبيه",
                    "Open encounters younger than the highlight threshold. They are not called overdue.",
                    "زيارات مفتوحة أصغر من حد التنبيه. لا تُسمى متأخرة.",
                    open, within, Coverage.COMPLETE, null, null, null, false);
        }
        if (facts.previousEncounters() == null || facts.previousEncountersCapped()) {
            indicators.unavailable("MISSING_CHIEF_COMPLAINT", "Closed encounters without a chief complaint", "زيارات مغلقة بلا شكوى رئيسية",
                    "A proxy for incomplete documentation: completed encounters with an empty chief complaint. It is not a full documentation audit.",
                    "مؤشر تقريبي لنقص التوثيق: زيارات مكتملة شكواها الرئيسية فارغة. ليس تدقيقاً كاملاً للتوثيق.",
                    previous, Coverage.PARTIAL, facts.previousEncountersCapped() ? "VOLUME_CAP" : "QUERY_FAILED", null, true);
            indicators.unavailable("UNBILLED_ENCOUNTERS", "Completed encounters not billed", "زيارات مكتملة غير مفوترة",
                    "Completed encounters whose billing status is still open.", "زيارات مكتملة حالة فوترتها ما زالت مفتوحة.", previous, Coverage.PARTIAL, "QUERY_FAILED", null, true);
        } else {
            long missingComplaint = facts.previousEncounters().stream().filter(row -> DailyBriefingCalculator.isCompleted(row.status()) && !row.chiefComplaintPresent()).count();
            long unbilled = facts.previousEncounters().stream().filter(row -> DailyBriefingCalculator.isCompleted(row.status()) && row.billingStatus() == EncounterBillingStatus.OPEN).count();
            indicators.count("MISSING_CHIEF_COMPLAINT", "Closed encounters without a chief complaint", "زيارات مغلقة بلا شكوى رئيسية",
                    "Completed encounters on the previous day with an empty chief complaint. This does not prove that every other document is complete.",
                    "زيارات مكتملة في اليوم السابق شكواها الرئيسية فارغة. هذا لا يثبت اكتمال بقية التوثيق.",
                    previous, missingComplaint, Coverage.PARTIAL, null, null, Drill.MISSING_CHIEF_COMPLAINT, true);
            Coverage moneyCoverage = facts.unbilledCharges() == null ? Coverage.PARTIAL : Coverage.COMPLETE;
            Long withCharge = facts.unbilledEncountersWithActiveCharge();
            String impact = withCharge == null ? indicators.pair("Financial impact was not read.", "لم يُقرأ الأثر المالي.")
                    : indicators.pair("Encounters with an active charge: " + withCharge + ". Encounters without one have no calculated amount.", "زيارات لها رسم نشط: " + withCharge + ". الزيارات بلا رسم ليس لها مبلغ محسوب.");
            if (withCharge != null && unbilled > withCharge) {
                moneyCoverage = Coverage.PARTIAL;
            }
            indicators.amount("UNBILLED_ENCOUNTERS", "Completed encounters not billed", "زيارات مكتملة غير مفوترة",
                    "Completed previous-day encounters whose billing status is open. The amount is the net of active charges where a charge exists; it is not invented when no charge exists.",
                    "زيارات مكتملة في اليوم السابق حالة فوترتها مفتوحة. المبلغ هو صافي الرسوم النشطة حين يوجد رسم، ولا يُختلق إذا لم يوجد رسم.",
                    previous, moneyRows(facts.unbilledCharges()), moneyCoverage, impact, null, true);
            indicators.count("UNBILLED_ENCOUNTER_COUNT", "Unbilled encounter count", "عدد الزيارات غير المفوترة",
                    "Count of those encounters. Kept separate from the amount so a missing charge is not turned into zero money.",
                    "عدد تلك الزيارات. منفصل عن المبلغ حتى لا يتحول الرسم الناقص إلى مبلغ صفر.",
                    previous, unbilled, moneyCoverage, impact, null, Drill.UNBILLED_ENCOUNTERS, false);
        }
        return section(indicators, "COMPLETION", "Encounter completion and unbilled services", "إكمال الزيارة والخدمات غير المفوترة",
                "Overdue work is counted separately from work still inside the highlight window.",
                "العمل المتجاوز يُحسب منفصلاً عن العمل الذي ما زال داخل نافذة التنبيه.",
                List.of(), List.of());
    }

    private static Section inpatient(BriefingContext context, DailyBriefingFacts facts, Indicators indicators) {
        indicators.begin("INPATIENT");
        DailyBriefingFacts.InpatientSnapshot inpatient = facts.inpatient();
        if (inpatient == null || inpatient.moduleUsed() == null) {
            return new Section("INPATIENT", indicators.pair("Inpatient and emergency", "التنويم والطوارئ"), true, null,
                    indicators.pair("Module presence could not be read, so no occupancy is shown as zero.", "تعذر قراءة وجود الوحدة، لذلك لا يُعرض الإشغال كصفر."),
                    List.of(indicators.unavailable("INPATIENT_MODULE", "Inpatient and emergency module", "وحدة التنويم والطوارئ", "Whether the facility has inpatient or emergency encounters.", "ما إذا كان للمنشأة زيارات تنويم أو طوارئ.", "facility", Coverage.PARTIAL, "QUERY_FAILED", null, false)),
                    List.of(), List.of());
        }
        if (!inpatient.moduleUsed()) {
            return new Section("INPATIENT", indicators.pair("Inpatient and emergency", "التنويم والطوارئ"), false,
                    indicators.pair("No inpatient or emergency encounters are stored for this facility, so this section is omitted.", "لا توجد زيارات تنويم أو طوارئ مخزنة لهذه المنشأة، لذلك يُحذف هذا القسم."),
                    null, List.of(), List.of(), List.of());
        }
        String previous = previousPeriod(context, indicators);
        String point = indicators.pair("Point in time " + context.occupancyAt(), "نقطة زمنية " + context.occupancyAt());
        indicators.count("ADMISSIONS", "Inpatient encounters started", "زيارات تنويم بدأت",
                "Inpatient encounters dated on the previous facility day. Bed-assignment events are not added to this count.",
                "زيارات تنويم بتاريخ اليوم السابق للمنشأة. أحداث تعيين السرير لا تُضاف إلى هذا العدد.",
                previous, inpatient.admissions(), Coverage.COMPLETE, null, null, Drill.ADMISSIONS, true);
        indicators.count("DISCHARGES", "Inpatient discharges", "خروج التنويم",
                "Inpatient encounters whose discharge timestamp falls on the previous facility day.",
                "زيارات تنويم وقع طابع خروجها في اليوم السابق للمنشأة.",
                previous, inpatient.discharges(), Coverage.COMPLETE, null, null, Drill.DISCHARGES, true);
        indicators.count("TRANSFERS", "Bed transfers", "تحويلات الأسرة",
                "Bed transactions of type transfer on the previous facility day.",
                "حركات أسرة من نوع تحويل في اليوم السابق للمنشأة.",
                previous, inpatient.transfers(), Coverage.COMPLETE, null, null, Drill.TRANSFERS, false);
        indicators.count("OCCUPIED_BEDS", "Occupied beds", "الأسرة المشغولة",
                "Distinct beds with an assignment covering the stated point in time.",
                "أسرة مميزة لها تعيين يغطي النقطة الزمنية المحددة.",
                point, inpatient.occupiedBeds(), Coverage.COMPLETE, null, null, null, true);
        indicators.unavailable("OPERATIONAL_BEDS", "Operational beds", "الأسرة التشغيلية",
                "Staffed beds.", "الأسرة المشغّلة.", point, Coverage.NOT_IN_SOURCE, "OPERATIONAL_BEDS_NOT_STORED", null, true);
        indicators.unavailable("AVAILABLE_BEDS", "Available beds", "الأسرة المتاحة",
                "Operational beds minus occupied beds.", "الأسرة التشغيلية ناقص المشغولة.", point, Coverage.NOT_IN_SOURCE, "OPERATIONAL_BEDS_NOT_STORED",
                indicators.pair("Available beds are not calculated from occupied beds alone.", "لا تُحسب الأسرة المتاحة من الأسرة المشغولة وحدها."), false);
        indicators.unavailable("OCCUPANCY_RATE", "Occupancy rate", "نسبة الإشغال",
                "Occupied beds divided by operational beds at the point in time.", "الأسرة المشغولة مقسومة على الأسرة التشغيلية في النقطة الزمنية.",
                point, Coverage.NOT_IN_SOURCE, "OPERATIONAL_BEDS_NOT_STORED",
                indicators.pair("The rate is omitted because operational beds are not stored. Occupied beds are not used as the denominator.", "تُحذف النسبة لأن الأسرة التشغيلية غير مخزنة. لا تُستخدم الأسرة المشغولة كمقام."), true);
        indicators.count("AWAITING_BED", "Patients awaiting a bed", "مرضى بانتظار سرير",
                "Open or in-progress inpatient encounters with no active bed assignment.",
                "زيارات تنويم مفتوحة أو قيد التنفيذ بلا تعيين سرير نشط.",
                point, inpatient.awaitingBed(), Coverage.COMPLETE, null, null, Drill.AWAITING_BED, true);
        indicators.count("ED_VISITS", "Emergency visits", "زيارات الطوارئ",
                "Emergency encounters dated on the previous facility day.",
                "زيارات طوارئ بتاريخ اليوم السابق للمنشأة.",
                previous, inpatient.edVisits(), Coverage.COMPLETE, null, null, Drill.ED_VISITS, true);
        los(indicators, "ALOS", "Average length of stay", "متوسط مدة الإقامة",
                "Mean hours from encounter start to discharge for inpatient encounters discharged on the previous day. Current inpatients are not included.",
                "متوسط الساعات من بدء الزيارة إلى الخروج لزيارات التنويم التي خرجت في اليوم السابق. المنومون الحاليون غير مشمولين.",
                previous, inpatient.alosHours(), inpatient.alosSample(), inpatient.alosExcluded(), "NO_DISCHARGES");
        los(indicators, "CURRENT_LOS", "Current length of stay", "مدة الإقامة الحالية",
                "Mean hours from encounter start to the refresh time for inpatients who are still open. Discharged patients are not included.",
                "متوسط الساعات من بدء الزيارة إلى وقت التحديث للمنومين الذين ما زالوا مفتوحين. المرضى الذين خرجوا غير مشمولين.",
                point, inpatient.currentLosHours(), inpatient.currentLosSample(), inpatient.currentLosExcluded(), "NO_CURRENT_INPATIENTS");
        return section(indicators, "INPATIENT", "Inpatient and emergency", "التنويم والطوارئ",
                "Included because this facility has inpatient or emergency encounters. Average length of stay and current length of stay are separate.",
                "يظهر لأن هذه المنشأة لديها زيارات تنويم أو طوارئ. متوسط مدة الإقامة ومدة الإقامة الحالية منفصلان.",
                List.of(), List.of());
    }

    private static void los(Indicators indicators, String code, String en, String ar, String definitionEn, String definitionAr, String period,
                            BigDecimal hours, Integer sample, Integer excluded, String emptyReason) {
        if (sample == null) {
            indicators.unavailable(code, en, ar, definitionEn, definitionAr, period, Coverage.PARTIAL, "QUERY_FAILED", null, true);
            return;
        }
        if (sample == 0) {
            indicators.unavailable(code, en, ar, definitionEn, definitionAr, period, Coverage.COMPLETE, emptyReason,
                    indicators.pair("There is no stay to average. The average is not reported as zero.", "لا توجد إقامة لحساب المتوسط. لا يُعرض المتوسط كصفر."), true);
            return;
        }
        indicators.count(code, en, ar, definitionEn, definitionAr, period, sample.longValue(), excluded != null && excluded > 0 ? Coverage.PARTIAL : Coverage.COMPLETE,
                indicators.pair("Mean hours: " + hours + ". Excluded stays: " + excluded + ".", "متوسط الساعات: " + hours + ". إقامات مستبعدة: " + excluded + "."),
                null, null, true);
    }

    private static Section comparisons(BriefingContext context, Indicators indicators) {
        indicators.begin("COMPARISONS");
        return section(indicators, "COMPARISONS", "Comparisons and targets", "المقارنات والأهداف",
                "Each compared indicator carries the previous value, the current value, and the absolute change. Percentage change is omitted when the comparison value is zero.",
                "كل مؤشر مقارن يحمل القيمة السابقة والقيمة الحالية والتغير المطلق. يُحذف التغير النسبي إذا كانت قيمة المقارنة صفراً.",
                List.of(),
                List.of(indicators.pair("No approved target is stored. Highlight thresholds in this request are not targets.", "لا يوجد هدف معتمد مخزن. حدود التنبيه في هذا الطلب ليست أهدافاً."),
                        indicators.pair("The comparison day is " + context.comparisonDay() + ", the same weekday in the previous week.", "يوم المقارنة هو " + context.comparisonDay() + "، نفس يوم الأسبوع في الأسبوع السابق.")));
    }

    private static List<Alert> exceptions(BriefingContext context, DailyBriefingFacts facts, Indicators indicators) {
        List<Alert> alerts = new ArrayList<>();
        if (facts.failures() != null && !facts.failures().isEmpty()) {
            alerts.add(alert(indicators, "DATA_DELAY", "HIGH", "OPERATIONAL",
                    indicators.pair("One or more figures could not be read.", "تعذر قراءة رقم أو أكثر."),
                    String.join(", ", facts.failures()),
                    indicators.pair("Impact was not calculated for the failed reads.", "لم يُحسب أثر القراءات التي فشلت."),
                    indicators.pair("Review the failed sources before relying on this briefing.", "راجع المصادر التي فشلت قبل الاعتماد على هذه الإحاطة.")));
        }
        rateAlert(alerts, indicators, "NO_SHOW_RATE", "NO_SHOW", "MEDIUM", "OPERATIONAL", context.noShowRatePercent());
        countAlert(alerts, indicators, "SCHEDULE_CONFLICTS", "SCHEDULE_CONFLICT", "MEDIUM", "OPERATIONAL",
                "Booked appointments overlap for the same practitioner.", "مواعيد محجوزة تتداخل لنفس الطبيب.",
                "Review the overlapping appointments.", "راجع المواعيد المتداخلة.");
        countAlert(alerts, indicators, "BOOKED_WITHOUT_PRACTITIONER", "MISSING_PRACTITIONER", "MEDIUM", "OPERATIONAL",
                "Appointments require a practitioner but none is recorded.", "مواعيد تتطلب طبيباً ولا يوجد طبيب مسجل.",
                "Review those bookings.", "راجع تلك الحجوزات.");
        countAlert(alerts, indicators, "AUTHORIZATION_DENIALS", "AUTH_DENIAL", "HIGH", "FINANCIAL",
                "Authorization denials are open.", "رفض موافقات ما زال مفتوحاً.",
                "Review the authorization denials. Do not treat them as claim denials.", "راجع رفض الموافقات. لا تعاملها كرفض مطالبات.");
        countAlert(alerts, indicators, "AUTH_AGING_15_PLUS", "AUTH_AGING", "MEDIUM", "FINANCIAL",
                "Pending authorizations are 15 days old or older.", "موافقات معلقة عمرها 15 يوماً أو أكثر.",
                "Review the aged authorizations.", "راجع الموافقات المتقادمة.");
        countAlert(alerts, indicators, "CLAIM_DENIALS", "CLAIM_DENIAL", "HIGH", "FINANCIAL",
                "Claims are in rejected status.", "مطالبات بحالة مرفوضة.",
                "Review the rejected claims. Approved and collected amounts are not available on the claim.", "راجع المطالبات المرفوضة. المبلغ المعتمد والمحصل غير متاحين على المطالبة.");
        countAlert(alerts, indicators, "CLAIM_FAILURES", "CLAIM_FAILURE", "MEDIUM", "FINANCIAL",
                "Claims are in failed status.", "مطالبات بحالة فشل.",
                "Review the failed submissions. The cause is not inferred.", "راجع الإرسالات الفاشلة. لا يُستنتج السبب.");
        countAlert(alerts, indicators, "READY_CLAIMS", "READY_CLAIMS", "LOW", "FINANCIAL",
                "Draft claims have not been submitted.", "مطالبات مسودة لم تُرسل.",
                "Review the draft claims.", "راجع المطالبات المسودة.");
        countAlert(alerts, indicators, "SERVICES_ON_HOLD", "SERVICES_ON_HOLD", "LOW", "OPERATIONAL",
                "Services are waiting for authorization.", "خدمات بانتظار موافقة.",
                "Review the held services.", "راجع الخدمات الموقوفة.");
        countAlert(alerts, indicators, "OVERDUE_ORDERS", "OVERDUE_ORDERS", "HIGH", "CLINICAL",
                "Diagnostic orders are older than the turnaround highlight.", "طلبات تشخيص أقدم من حد الإنجاز.",
                "Review the overdue orders and their department.", "راجع الطلبات المتأخرة وقسمها.");
        countAlert(alerts, indicators, "OPEN_ENCOUNTERS", "OPEN_ENCOUNTERS", "MEDIUM", "OPERATIONAL",
                "Encounters are open beyond the highlight threshold.", "زيارات مفتوحة بعد حد التنبيه.",
                "Review the open encounters.", "راجع الزيارات المفتوحة.");
        countAlert(alerts, indicators, "UNBILLED_ENCOUNTER_COUNT", "UNBILLED", "MEDIUM", "FINANCIAL",
                "Completed encounters are still unbilled.", "زيارات مكتملة ما زالت غير مفوترة.",
                "Review the unbilled encounters. Do not invent a charge where none is stored.", "راجع الزيارات غير المفوترة. لا تختلق رسماً إذا لم يكن مخزناً.");
        countAlert(alerts, indicators, "AWAITING_BED", "AWAITING_BED", "MEDIUM", "OPERATIONAL",
                "Inpatients have no active bed.", "منومون بلا سرير نشط.",
                "Review the patients awaiting a bed.", "راجع المرضى الذين ينتظرون سريراً.");
        alerts.sort(Comparator.comparingInt(DailyBriefingAssembler::priorityRank).thenComparing(Alert::code));
        return alerts;
    }

    private static void rateAlert(List<Alert> alerts, Indicators indicators, String code, String alertCode, String priority, String category, int threshold) {
        Indicator indicator = indicators.find(code);
        if (indicator == null || indicator.rate() == null || indicator.rate().compareTo(BigDecimal.valueOf(threshold)) <= 0) {
            return;
        }
        alerts.add(alert(indicators, alertCode, priority, category,
                indicators.pair("The no-show rate is above the highlight threshold of " + threshold + "%.", "نسبة عدم الحضور أعلى من حد التنبيه " + threshold + "%."),
                indicators.pair("Rate " + indicator.rate() + " from " + indicator.count() + " no-shows.", "النسبة " + indicator.rate() + " من " + indicator.count() + " حالات عدم حضور."),
                indicators.pair("Operational. Lost revenue was not calculated because it is not stored on the appointment.", "تشغيلي. لم يُحسب الإيراد الفائت لأنه غير مخزن على الموعد."),
                indicators.pair("Review the previous day's no-show appointments.", "راجع مواعيد عدم الحضور لليوم السابق.")));
    }

    private static void countAlert(List<Alert> alerts, Indicators indicators, String code, String alertCode, String priority, String category, String issueEn, String issueAr, String actionEn, String actionAr) {
        Indicator indicator = indicators.find(code);
        if (indicator == null || indicator.count() == null || indicator.count() <= 0 || indicator.valueState() == DailyBriefingResponse.ValueState.UNAVAILABLE) {
            return;
        }
        alerts.add(alert(indicators, alertCode, priority, category, indicators.pair(issueEn, issueAr),
                indicators.pair("Count " + indicator.count() + ".", "العدد " + indicator.count() + "."),
                category,
                indicators.pair(actionEn, actionAr)));
    }

    private static Alert alert(Indicators indicators, String code, String priority, String category, String issue, String evidence, String impact, String action) {
        String unknown = indicators.pair("Unknown. It is not recorded.", "غير معروف. غير مسجّل في النظام.");
        String recommendation = action + " " + indicators.pair("This is a recommendation for review. It does not change records or send clinical instructions.", "هذه توصية للمراجعة. لا تعدل السجلات ولا ترسل تعليمات سريرية.");
        return new Alert(code, priority, category, issue, evidence, impact, recommendation, true, false, false, false, unknown, false, unknown, false, null);
    }

    private static int priorityRank(Alert alert) {
        return switch (alert.priority()) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private static List<ActionItem> actions(List<Alert> alerts) {
        return alerts.stream().map(alert -> new ActionItem(
                alert.code(), alert.issue(), alert.evidence(), alert.impact(), alert.recommendedAction(),
                true, false, false, false, alert.cause(), false, alert.owner(), false, null
        )).toList();
    }

    private static ExecutiveSummary summary(BriefingContext context, Indicators indicators, List<Indicator> headlines, List<Alert> alerts) {
        List<String> changes = headlines.stream()
                .filter(indicator -> indicator.comparison() != null && indicator.comparison().absoluteChange() != null)
                .sorted(Comparator.comparing(DailyBriefingAssembler::changeRank).reversed())
                .limit(3)
                .map(indicator -> changeText(indicators, indicator))
                .toList();
        List<String> priorities = alerts.stream().limit(3).map(Alert::issue).toList();
        if (priorities.isEmpty()) {
            priorities = List.of(indicators.pair(
                    "No exception met a highlight threshold. No extra priorities were added.",
                    "لا يوجد استثناء بلغ حد تنبيه. لم تُضف أولويات إضافية."
            ));
        }
        String narrative = indicators.pair(
                "Briefing for " + context.facilityName() + ". Previous day " + context.previousDay() + " is a complete facility day. Current day " + context.currentDay()
                        + (context.currentDayPartial() ? " is partial through " : " is complete through ") + context.refreshedAt()
                        + " (" + context.timeZone() + ", source " + context.timeZoneSource() + "). Figures come from stored hospital records. They are not estimated.",
                "إحاطة " + context.facilityName() + ". اليوم السابق " + context.previousDay() + " يوم منشأة مكتمل. اليوم الحالي " + context.currentDay()
                        + (context.currentDayPartial() ? " جزئي حتى " : " مكتمل حتى ") + context.refreshedAt()
                        + " (" + context.timeZone() + "، المصدر " + context.timeZoneSource() + "). الأرقام من سجلات المستشفى المخزنة. ليست تقديرات."
        );
        return new ExecutiveSummary(narrative, changes, priorities);
    }

    private static String changeText(Indicators indicators, Indicator indicator) {
        Comparison comparison = indicator.comparison();
        String percent = comparison.percentageChange() == null
                ? indicators.pair("Percentage change was not calculated (" + comparison.percentageOmittedReason() + ").", "لم يُحسب التغير النسبي (" + comparison.percentageOmittedReason() + ").")
                : indicators.pair("Percentage change " + comparison.percentageChange() + "%.", "التغير النسبي " + comparison.percentageChange() + "%.");
        return indicator.name() + ": " + comparison.baseline() + " -> " + comparison.current() + ". "
                + indicators.pair("Absolute change ", "التغير المطلق ") + comparison.absoluteChange() + ". " + percent;
    }

    private static BigDecimal changeRank(Indicator indicator) {
        Comparison comparison = indicator.comparison();
        BigDecimal percent = comparison.percentageChange();
        if (percent != null) {
            return percent.abs();
        }
        return comparison.absoluteChange().abs();
    }

    private static String status(BriefingContext context, DailyBriefingFacts facts, List<Indicator> headlines) {
        if (facts.failures() != null && !facts.failures().isEmpty()) {
            return "DATA_DELAYED";
        }
        boolean partial = context.currentDayPartial() || headlines.stream().anyMatch(indicator -> indicator.coverage() != Coverage.COMPLETE);
        return partial ? "PARTIAL" : "CALCULATED";
    }

    private static DataQuality quality(BriefingContext context, DailyBriefingFacts facts, Indicators indicators) {
        return new DataQuality(
                context.timeZone(),
                context.timeZoneSource(),
                context.refreshedAt(),
                true,
                context.currentDayPartial(),
                indicators.pair("Every figure is a query or a rule over query results. A true zero is a successful read with no rows. A missing read stays unavailable. AI is not used to produce numbers, causes, owners, or dates.",
                        "كل رقم هو استعلام أو قاعدة على نتائج استعلام. الصفر الحقيقي قراءة ناجحة بلا صفوف. القراءة الناقصة تبقى غير متاحة. لا يُستخدم الذكاء الاصطناعي لإنتاج أرقام أو أسباب أو مالكين أو تواريخ."),
                indicators.pair("The summary is aggregated. Case identifiers are returned only by the audited cases request. Patient names and clinical text are not included, and this briefing does not send notifications.",
                        "الملخص مجمع. معرفات الحالات تُعاد فقط من طلب الحالات المدقق. أسماء المرضى والنص السريري غير مشمولة، وهذه الإحاطة لا ترسل إشعارات."),
                indicators.pair("Unacknowledged critical results are withheld because acknowledgment of a critical result is not a stored field. This report does not replace real-time critical-result escalation.",
                        "تُحجب النتائج الحرجة غير مؤكدة الاستلام لأن تأكيد النتيجة الحرجة ليس حقلاً مخزناً. هذا التقرير لا يغني عن تصعيد النتائج الحرجة الفوري."),
                facts.failures(),
                List.of(indicators.pair("Highlight thresholds are request settings, not approved targets.", "حدود التنبيه إعدادات للطلب وليست أهدافاً معتمدة.")),
                context.permissionNote()
        );
    }

    private static Issuance issuance(BriefingContext context, String status, List<String> failures) {
        List<String> errors = new ArrayList<>();
        if (failures != null) {
            errors.addAll(failures);
        }
        if (context.currentDayPartial()) {
            errors.add("CURRENT_DAY_PARTIAL");
        }
        return new Issuance(status, context.refreshedAt(), context.currentDay(), context.previousDay(), context.comparisonDay(),
                context.timeZone(), context.timeZoneSource(), true, context.currentDayPartial(), context.occupancyAt(), List.copyOf(errors), DUPLICATE_POLICY);
    }

    private static AppliedFilters filters(BriefingContext context) {
        return new AppliedFilters(context.facilityId(), context.facilityName(), context.departmentId(), context.practitionerId(), context.specialty(), null,
                "ar".equals(context.language()) ? "الفرع ليس بعداً مخزناً في خدمة المرضى." : "Branch is not a stored dimension in the patient service.");
    }

    private static HighlightThresholds thresholds(BriefingContext context, Indicators indicators) {
        return new HighlightThresholds(context.openEncounterHours(), context.noShowRatePercent(), context.waitingMinutes(), context.turnaroundMinutes(),
                indicators.pair("No approved target is stored.", "لا يوجد هدف معتمد مخزن."));
    }

    private static Section section(Indicators indicators, String code, String en, String ar, String definitionEn, String definitionAr, List<Breakdown> breakdowns, List<String> notes) {
        return new Section(code, indicators.pair(en, ar), true, null, indicators.pair(definitionEn, definitionAr), indicators.in(code), breakdowns, notes);
    }

    private static Section placeholder(Indicators indicators, String code, String en, String ar, List<String> notes) {
        return new Section(code, indicators.pair(en, ar), true, null, null, List.of(), List.of(), notes);
    }

    private static Breakdown breakdown(Indicators indicators, String code, String en, String ar, Coverage coverage, String reason, List<DimensionRow> rows) {
        return new Breakdown(code, indicators.pair(en, ar), coverage, reason, rows);
    }

    private static Comparison comparison(String baselinePeriod, DailyBriefingCalculator.ComparisonResult result) {
        if (result == null) {
            return null;
        }
        return new Comparison(baselinePeriod, result.baseline(), result.current(), result.absoluteChange(), result.percentageChange(), result.percentageOmittedReason());
    }

    private static Comparison rateComparison(String baselinePeriod, DailyBriefingCalculator.RateResult current, DailyBriefingFacts.ComparisonCounts comparison) {
        if (comparison == null || comparison.noShows() == null || comparison.eligibleAppointments() == null) {
            return new Comparison(baselinePeriod, null, current == null ? null : current.rate(), null, null, "VALUE_MISSING");
        }
        DailyBriefingCalculator.RateResult baseline = DailyBriefingCalculator.rate(comparison.noShows(), comparison.eligibleAppointments());
        if (current == null || current.rate() == null || baseline.rate() == null) {
            String reason = baseline.omittedReason() != null ? baseline.omittedReason() : current == null ? "VALUE_MISSING" : current.omittedReason();
            return new Comparison(baselinePeriod, baseline.rate(), current == null ? null : current.rate(), null, null, reason);
        }
        return comparison(baselinePeriod, DailyBriefingCalculator.compare(current.rate(), baseline.rate()));
    }

    private static List<DimensionRow> countBy(List<DailyBriefingFacts.EncounterRow> rows, java.util.function.Function<DailyBriefingFacts.EncounterRow, String> key, java.util.function.Function<DailyBriefingFacts.EncounterRow, String> label) {
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (DailyBriefingFacts.EncounterRow row : rows) {
            String id = key.apply(row);
            labels.putIfAbsent(id, label.apply(row));
            counts.computeIfAbsent(id, ignored -> new long[1])[0]++;
        }
        return counts.entrySet().stream().map(entry -> new DimensionRow(entry.getKey(), labels.get(entry.getKey()), entry.getValue()[0], null, null)).toList();
    }

    private static List<DimensionRow> departmentRows(List<DailyBriefingFacts.DimensionCount> rows) {
        if (rows == null) {
            return List.of();
        }
        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (DailyBriefingFacts.DimensionCount row : rows) {
            if (!DailyBriefingCalculator.countsAsEligibleAppointment(safeStatus(row.status()))) {
                continue;
            }
            labels.putIfAbsent(row.key(), row.label());
            counts.computeIfAbsent(row.key(), ignored -> new long[1])[0] += row.count();
        }
        return counts.entrySet().stream().map(entry -> new DimensionRow(entry.getKey(), labels.get(entry.getKey()), entry.getValue()[0], null, null)).toList();
    }

    private static List<DimensionRow> sessionRows(List<Instant> starts, ZoneId zone) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Instant start : starts) {
            if (start == null) {
                continue;
            }
            counts.merge(DailyBriefingCalculator.session(start.atZone(zone).getHour()), 1L, Long::sum);
        }
        return counts.entrySet().stream().map(entry -> new DimensionRow(entry.getKey(), entry.getKey(), entry.getValue(), null, null)).toList();
    }

    private static List<DimensionRow> claimReasons(List<DailyBriefingFacts.ClaimRow> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (DailyBriefingFacts.ClaimRow row : rows) {
            if (!"REJECTED".equals(row.status())) {
                continue;
            }
            String reason = row.outcome() == null || row.outcome().isBlank() ? "UNKNOWN" : row.outcome();
            counts.merge(reason, row.count(), Long::sum);
        }
        return counts.entrySet().stream().map(entry -> new DimensionRow(entry.getKey(), entry.getKey(), entry.getValue(), null, null)).toList();
    }

    private static long claimCount(List<DailyBriefingFacts.ClaimRow> rows, String status) {
        return rows.stream().filter(row -> status.equals(row.status())).mapToLong(DailyBriefingFacts.ClaimRow::count).sum();
    }

    private static List<MoneyAmount> claimMoney(List<DailyBriefingFacts.ClaimRow> rows) {
        if (rows == null) {
            return null;
        }
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (DailyBriefingFacts.ClaimRow row : rows) {
            String currency = "UNSPECIFIED";
            amounts.merge(currency, zero(row.claimedNet()), BigDecimal::add);
            counts.merge(currency, row.count(), Long::sum);
        }
        if (amounts.isEmpty()) {
            return List.of();
        }
        return amounts.entrySet().stream().map(entry -> new MoneyAmount(entry.getKey(), entry.getValue(), counts.get(entry.getKey()), null)).toList();
    }

    private enum ChargePart { GROSS, DISCOUNT, EXEMPTION }

    private static List<MoneyAmount> money(List<DailyBriefingFacts.ChargeRow> rows, ChargePart part) {
        if (rows == null) {
            return null;
        }
        return rows.stream().map(row -> new MoneyAmount(currency(row.currency()), switch (part) {
            case GROSS -> zero(row.gross());
            case DISCOUNT -> zero(row.discount());
            case EXEMPTION -> zero(row.exemption());
        }, row.count(), null)).toList();
    }

    private static List<MoneyAmount> net(DailyBriefingFacts.BillingSnapshot billing) {
        if (billing.activeCharges() == null || billing.creditNotes() == null) {
            return null;
        }
        Map<String, BigDecimal> gross = new LinkedHashMap<>();
        Map<String, BigDecimal> discount = new LinkedHashMap<>();
        Map<String, BigDecimal> exemption = new LinkedHashMap<>();
        Map<String, BigDecimal> credit = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();
        if (billing.activeCharges() != null) {
            for (DailyBriefingFacts.ChargeRow row : billing.activeCharges()) {
                String currency = currency(row.currency());
                gross.merge(currency, zero(row.gross()), BigDecimal::add);
                discount.merge(currency, zero(row.discount()), BigDecimal::add);
                exemption.merge(currency, zero(row.exemption()), BigDecimal::add);
                counts.merge(currency, row.count(), Long::sum);
            }
        }
        if (billing.creditNotes() != null) {
            for (DailyBriefingFacts.MoneyRow row : billing.creditNotes()) {
                credit.merge(currency(row.currency()), zero(row.amount()), BigDecimal::add);
                counts.putIfAbsent(currency(row.currency()), 0L);
            }
        }
        List<MoneyAmount> result = new ArrayList<>();
        for (String currency : counts.keySet()) {
            result.add(new MoneyAmount(currency, DailyBriefingCalculator.netBilling(gross.get(currency), discount.get(currency), exemption.get(currency), credit.get(currency)), counts.get(currency), null));
        }
        return result;
    }

    private static List<MoneyAmount> moneyRows(List<DailyBriefingFacts.MoneyRow> rows) {
        if (rows == null) {
            return null;
        }
        return rows.stream().map(row -> new MoneyAmount(currency(row.currency()), zero(row.amount()), row.count(), row.label())).toList();
    }

    private static List<MoneyAmount> labeled(List<DailyBriefingFacts.MoneyRow> rows, String label) {
        if (rows == null) {
            return null;
        }
        return moneyRows(rows.stream().filter(row -> label.equals(row.label())).toList());
    }

    private static List<MoneyAmount> labeledExcept(List<DailyBriefingFacts.MoneyRow> rows, String label) {
        if (rows == null) {
            return null;
        }
        return moneyRows(rows.stream().filter(row -> !label.equals(row.label())).toList());
    }

    private static AppointmentStatus safeStatus(String status) {
        try {
            return status == null ? null : AppointmentStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String previousPeriod(BriefingContext context, Indicators indicators) {
        return context.previousDay() + " " + indicators.pair("complete facility day", "يوم منشأة مكتمل") + " (" + context.timeZone() + ")";
    }

    private static String currentPeriod(BriefingContext context, Indicators indicators) {
        String state = context.currentDayPartial() ? indicators.pair("partial current day", "يوم حالي غير مكتمل") : indicators.pair("complete facility day", "يوم منشأة مكتمل");
        return context.currentDay() + " " + state + " (" + context.timeZone() + ")";
    }

    private static String comparisonPeriod(BriefingContext context, Indicators indicators) {
        return context.comparisonDay() + " " + indicators.pair("same weekday last week", "نفس يوم الأسبوع في الأسبوع السابق");
    }

    private static String id(Long value) {
        return value == null ? "UNKNOWN" : value.toString();
    }

    private static String name(String label, Long id) {
        return label == null || label.isBlank() ? id(id) : label;
    }

    private static String enumName(Enum<?> value) {
        return value == null ? "UNKNOWN" : value.name();
    }

    private static String currency(String value) {
        return value == null || value.isBlank() ? "UNSPECIFIED" : value;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
