package com.dazzle.asklepios.service.dailybriefing;

import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Comparison;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Coverage;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Drill;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.Indicator;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.MoneyAmount;
import com.dazzle.asklepios.service.dto.dailybriefing.DailyBriefingResponse.ValueState;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class Indicators {

    private final BriefingContext context;
    private final List<Indicator> all = new ArrayList<>();
    private final List<String> sections = new ArrayList<>();
    private final List<Indicator> headlines = new ArrayList<>();
    private String currentSection = "REPORT";

    Indicators(BriefingContext context) {
        this.context = context;
    }

    void begin(String section) {
        this.currentSection = section;
    }

    List<Indicator> in(String section) {
        List<Indicator> matches = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (section.equals(sections.get(i)) && calculated(all.get(i))) {
                matches.add(all.get(i));
            }
        }
        return matches;
    }

    List<Indicator> all() {
        return List.copyOf(all);
    }

    List<Indicator> headlines() {
        return headlines.stream().filter(Indicators::calculated).toList();
    }

    private static boolean calculated(Indicator indicator) {
        return indicator.valueState() != ValueState.UNAVAILABLE;
    }

    Indicator find(String code) {
        return all.stream().filter(indicator -> indicator.code().equals(code)).findFirst().orElse(null);
    }

    Indicator count(String code, String nameEn, String nameAr, String definitionEn, String definitionAr, String period,
                    Long count, Coverage coverage, String note, Comparison comparison, Drill drill, boolean headline) {
        ValueState state = count == null ? ValueState.UNAVAILABLE : count == 0 ? ValueState.TRUE_ZERO : ValueState.VALUE;
        String reason = count == null ? "QUERY_FAILED" : null;
        return add(code, nameEn, nameAr, definitionEn, definitionAr, period, state, coverage, count, null, null, List.of(), null, "count", note, reason, comparison, drill, headline);
    }

    Indicator unavailable(String code, String nameEn, String nameAr, String definitionEn, String definitionAr, String period,
                          Coverage coverage, String reason, String note, boolean headline) {
        return add(code, nameEn, nameAr, definitionEn, definitionAr, period, ValueState.UNAVAILABLE, coverage, null, null, null, List.of(), null, null, note, reason, null, null, headline);
    }

    Indicator amount(String code, String nameEn, String nameAr, String definitionEn, String definitionAr, String period,
                     List<MoneyAmount> amounts, Coverage coverage, String note, Comparison comparison, boolean headline) {
        List<MoneyAmount> rows = amounts == null ? List.of() : amounts;
        if (amounts == null) {
            return unavailable(code, nameEn, nameAr, definitionEn, definitionAr, period, coverage, "QUERY_FAILED", note, headline);
        }
        if (rows.isEmpty()) {
            return add(code, nameEn, nameAr, definitionEn, definitionAr, period, ValueState.TRUE_ZERO, coverage, 0L, BigDecimal.ZERO, null, List.of(), null, context.language().equals("ar") ? "مبلغ" : "amount", note, null, comparison, null, headline);
        }
        if (rows.size() == 1) {
            MoneyAmount only = rows.get(0);
            return add(code, nameEn, nameAr, definitionEn, definitionAr, period, ValueState.VALUE, coverage, only.count(), only.amount(), only.currency(), rows, null, only.currency(), note, null, comparison, null, headline);
        }
        boolean oneCurrency = rows.stream().map(MoneyAmount::currency).distinct().count() == 1;
        if (oneCurrency) {
            BigDecimal total = rows.stream().map(row -> row.amount() == null ? BigDecimal.ZERO : row.amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
            long count = rows.stream().map(MoneyAmount::count).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
            return add(code, nameEn, nameAr, definitionEn, definitionAr, period, ValueState.VALUE, coverage, count, total, rows.get(0).currency(), rows, null, rows.get(0).currency(), note, null, comparison, null, headline);
        }
        String multi = pair("Amounts are not combined across currencies.", "لا تُجمع المبالغ بين العملات.");
        return add(code, nameEn, nameAr, definitionEn, definitionAr, period, ValueState.VALUE, coverage, null, null, null, rows, null, "currency", joinNote(note, multi), null, comparison, null, headline);
    }

    Indicator rate(String code, String nameEn, String nameAr, String definitionEn, String definitionAr, String period,
                   DailyBriefingCalculator.RateResult result, Long numerator, Long denominator, Coverage coverage, String note, Comparison comparison, boolean headline) {
        if (result == null || result.rate() == null) {
            String reason = result == null ? "QUERY_FAILED" : result.omittedReason();
            return add(code, nameEn, nameAr, definitionEn, definitionAr, period, ValueState.UNAVAILABLE, coverage, numerator, null, null, List.of(), null, "percent", note, reason, comparison, null, headline);
        }
        return add(code, nameEn, nameAr, definitionEn, definitionAr, period, ValueState.VALUE, coverage, numerator, null, null, List.of(), result.rate(), "percent", note, null, comparison, null, headline);
    }

    private Indicator add(String code, String nameEn, String nameAr, String definitionEn, String definitionAr, String period,
                          ValueState state, Coverage coverage, Long count, BigDecimal amount, String currency, List<MoneyAmount> amounts,
                          BigDecimal rate, String unit, String note, String reason, Comparison comparison, Drill drill, boolean headline) {
        Indicator indicator = new Indicator(
                code,
                pair(nameEn, nameAr),
                pair(definitionEn, definitionAr),
                pair("HIS patient-service", "نظام معلومات المستشفى — خدمة المرضى"),
                period,
                context.refreshedAt(),
                state,
                coverage,
                count,
                amount,
                currency,
                amounts,
                rate,
                unit,
                note,
                reason,
                comparison,
                drill == null ? null : drillPath(drill)
        );
        all.add(indicator);
        sections.add(currentSection);
        if (headline) {
            headlines.add(indicator);
        }
        return indicator;
    }

    private String drillPath(Drill drill) {
        StringBuilder path = new StringBuilder("/api/patient/daily-briefing/cases?indicator=")
                .append(drill.name())
                .append("&facilityId=").append(context.facilityId());
        if (context.departmentId() != null) {
            path.append("&departmentId=").append(context.departmentId());
        }
        if (context.practitionerId() != null) {
            path.append("&practitionerId=").append(context.practitionerId());
        }
        if (context.specialty() != null) {
            path.append("&specialty=").append(URLEncoder.encode(context.specialty(), StandardCharsets.UTF_8));
        }
        if (context.reportDate() != null) {
            path.append("&reportDate=").append(context.reportDate());
        }
        return path.toString();
    }

    String pair(String english, String arabic) {
        return "ar".equals(context.language()) ? arabic : english;
    }

    private static String joinNote(String left, String right) {
        if (left == null || left.isBlank()) {
            return right;
        }
        return left + " " + right;
    }
}
