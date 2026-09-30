package com.dazzle.asklepios.integration.waseel.service.pdf;

import com.dazzle.asklepios.integration.waseel.service.pdf.InvestigationResultsReport.LabResultRow;
import com.dazzle.asklepios.integration.waseel.service.pdf.InvestigationResultsReport.OrderSection;
import com.dazzle.asklepios.integration.waseel.service.pdf.InvestigationResultsReport.OrderTestSection;
import com.dazzle.asklepios.integration.waseel.service.pdf.InvestigationResultsReport.RadiologyResult;
import com.dazzle.asklepios.integration.waseel.service.pdf.SimplePdfWriter.Color;
import com.dazzle.asklepios.integration.waseel.service.pdf.SimplePdfWriter.Font;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders lab / radiology investigation results as a readable PDF attachment for Waseel claims.
 * Layout: patient header, then per order an info strip (order / encounter / from department),
 * and per order test a titled section with its results table (lab) or report (radiology).
 */
@Component
public class InvestigationResultsPdfRenderer {

    private final int maxPdfBytes = 1_000_000;
    private final int initialMaxPages = 200;
    private final ZoneId reportZone = ZoneId.of("Asia/Riyadh");
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);
    private final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.ENGLISH);

    private final float margin = 36f;
    private final float contentWidth = 595.28f - 2 * margin;
    private final float top = 841.89f - margin;
    private final float bottom = 58f;

    private final float boxInset = 10f;
    private final float boxX = margin + boxInset;
    private final float boxWidth = contentWidth - 2 * boxInset;
    private final float boxRadius = 6f;
    private final float cellPaddingX = 10f;
    private final float cellPaddingY = 8f;
    private final int maxLinesPerCell = 30;

    private final float testHeaderHeight = 28f;
    private final float tableHeaderHeight = 24f;
    private final float pillHeight = 12f;

    private final String[] labHeaders = {"TEST", "RESULT", "UNIT", "NORMAL RANGE", "RESULT DATE"};
    private final float[] labWidths = labColumnWidths();

    private final Color titleColor = new Color(0x12344D);
    private final Color textColor = new Color(0x1F2933);
    private final Color mutedColor = new Color(0x7B8794);
    private final Color labelColor = new Color(0x8A94A6);
    private final Color accentColor = new Color(0x0B9BD8);
    private final Color accentTextColor = new Color(0x0A8BCB);
    private final Color testHeaderBg = new Color(0xEAF4FC);
    private final Color infoBg = new Color(0xF7FAFD);
    private final Color infoBorder = new Color(0xC7DBEE);
    private final Color cardBorder = new Color(0xD9E2EC);
    private final Color dividerColor = new Color(0xE4E9EF);
    private final Color pillBg = new Color(0xEEF1F5);
    private final Color pillBorder = new Color(0xD5DAE1);
    private final Color pillText = new Color(0x4A5568);
    private final Color abnormalColor = new Color(0xD93025);
    private final Color abnormalBg = new Color(0xFDECEA);
    private final Color abnormalBorder = new Color(0xF5C2BE);

    public boolean withinSizeLimit(byte[] pdf) {
        return pdf != null && pdf.length <= maxPdfBytes;
    }

    public byte[] render(InvestigationResultsReport report) {
        int maxPages = initialMaxPages;
        while (true) {
            byte[] bytes = new Layout(report, maxPages, Instant.now()).render();
            if (withinSizeLimit(bytes) || maxPages == 1) {
                return bytes;
            }
            maxPages = Math.max(1, maxPages / 2);
        }
    }

    private float[] labColumnWidths() {
        float[] widths = {140f, 104f, 54f, 94f, 0f};
        float used = 0f;
        for (int i = 0; i < widths.length - 1; i++) {
            used += widths[i];
        }
        widths[widths.length - 1] = boxWidth - used;
        return widths;
    }

    private final class Layout {

        private final InvestigationResultsReport report;
        private final SimplePdfWriter pdf;
        private final Instant generatedAt;
        private float y;
        private boolean truncated;

        private float boxTop = Float.NaN;
        private Runnable boxBeforeRestart;
        private Runnable boxAfterRestart;

        private Layout(InvestigationResultsReport report, int maxPages, Instant generatedAt) {
            this.report = report;
            this.pdf = new SimplePdfWriter(maxPages);
            this.generatedAt = generatedAt;
        }

        private byte[] render() {
            try {
                startPage();
                drawTitle();
                drawPatientCard();
                List<OrderSection> orders = report.orders() == null ? List.of() : report.orders();
                for (OrderSection order : orders) {
                    drawOrder(order);
                }
            } catch (SimplePdfWriter.PageLimitReachedException e) {
                truncated = true;
            }
            drawFooters();
            return pdf.toBytes();
        }

        // ---------------------------------------------------------------- page flow

        private void startPage() {
            pdf.newPage();
            y = top;
        }

        /**
         * Makes sure {@code height} fits on the current page. Inside an open box the box border is closed on the
         * current page and re-opened on the next one, after re-drawing the section's continuation header.
         */
        private void reserve(float height) {
            if (y - height >= bottom) {
                return;
            }
            if (Float.isNaN(boxTop)) {
                startPage();
                return;
            }
            closeBoxBorder();
            startPage();
            if (boxBeforeRestart != null) {
                boxBeforeRestart.run();
            }
            boxTop = y;
            if (boxAfterRestart != null) {
                boxAfterRestart.run();
            }
        }

        private void beginBox(Runnable beforeRestart, Runnable afterRestart) {
            boxTop = y;
            boxBeforeRestart = beforeRestart;
            boxAfterRestart = afterRestart;
        }

        private void endBox() {
            closeBoxBorder();
            boxTop = Float.NaN;
            boxBeforeRestart = null;
            boxAfterRestart = null;
        }

        private void closeBoxBorder() {
            if (!Float.isNaN(boxTop) && boxTop - y > 1f) {
                pdf.roundedRect(boxX, y, boxWidth, boxTop - y, boxRadius, null, cardBorder, 0.8f);
            }
        }

        // ---------------------------------------------------------------- header / patient

        private void drawTitle() {
            pdf.text(margin, y - 18, Font.BOLD, 17, titleColor, "Investigation Results");
            pdf.text(margin, y - 32, Font.REGULAR, 8.5f, mutedColor, "Laboratory and radiology results");

            String generated = "Generated: " + formatDateTime(generatedAt);
            float generatedWidth = pdf.textWidth(generated, Font.REGULAR, 8);
            pdf.text(margin + contentWidth - generatedWidth, y - 16, Font.REGULAR, 8, mutedColor, generated);

            y -= 42;
            pdf.line(margin, y, margin + contentWidth, y, 1.5f, accentColor);
            y -= 14;
        }

        private void drawPatientCard() {
            String[][] cells = {
                    {"PATIENT NAME", valueOrDash(report.patientName())},
                    {"MRN", valueOrDash(report.medicalRecordNumber())},
                    {"ENCOUNTER", valueOrDash(report.encounterNumber())},
                    {"DATE OF BIRTH", formatDate(report.dateOfBirth())},
                    {"GENDER", valueOrDash(report.gender())},
                    {"ENCOUNTER DATE", formatDate(report.encounterDate())}
            };

            float rowHeight = 28f;
            float height = 2 * rowHeight + 10f;
            float columnWidth = contentWidth / 3;
            pdf.roundedRect(margin, y - height, contentWidth, height, boxRadius, infoBg, infoBorder, 0.8f);

            for (int i = 0; i < cells.length; i++) {
                float x = margin + (i % 3) * columnWidth + 12;
                float rowTop = y - 6 - (i / 3) * rowHeight;
                pdf.text(x, rowTop - 10, Font.BOLD, 7, labelColor, cells[i][0]);
                pdf.text(x, rowTop - 22, Font.BOLD, 9.5f, textColor,
                        fit(cells[i][1], Font.BOLD, 9.5f, columnWidth - 20));
            }

            y -= height + 18;
        }

        // ---------------------------------------------------------------- order

        private void drawOrder(OrderSection order) {
            List<OrderTestSection> tests = order.orderTests() == null ? List.of() : order.orderTests().stream()
                    .filter(OrderTestSection::hasResults)
                    .toList();
            if (tests.isEmpty()) {
                return;
            }

            reserve(40f + testHeaderHeight + tableHeaderHeight + 50f);
            drawOrderInfoStrip(order);

            for (int i = 0; i < tests.size(); i++) {
                if (i > 0) {
                    reserve(14f);
                    pdf.dashedLine(margin, y - 6, margin + contentWidth, y - 6, 0.7f, infoBorder, 3f);
                    y -= 14;
                }
                drawOrderTest(tests.get(i));
            }

            y -= 12;
        }

        private void drawOrderInfoStrip(OrderSection order) {
            String[][] cells = {
                    {"ORDER", valueOrDash(order.orderNumber())},
                    {"ENCOUNTER", valueOrDash(order.encounterNumber())},
                    {"FROM DEPARTMENT", valueOrDash(order.fromDepartment()).toUpperCase(Locale.ROOT)}
            };

            float height = 36f;
            float columnWidth = contentWidth / 3;
            pdf.roundedRect(margin, y - height, contentWidth, height, boxRadius, infoBg, infoBorder, 0.8f);
            for (int i = 1; i < cells.length; i++) {
                float x = margin + i * columnWidth;
                pdf.line(x, y, x, y - height, 0.8f, infoBorder);
            }
            for (int i = 0; i < cells.length; i++) {
                float x = margin + i * columnWidth + 10;
                pdf.text(x, y - 13, Font.BOLD, 7, labelColor, cells[i][0]);
                pdf.text(x, y - 27, Font.BOLD, 9.5f, textColor, fit(cells[i][1], Font.BOLD, 9.5f, columnWidth - 20));
            }
            y -= height;
        }

        private void drawOrderTest(OrderTestSection section) {
            boolean hasLab = section.labResults() != null && !section.labResults().isEmpty();
            reserve(testHeaderHeight + 8f + (hasLab ? tableHeaderHeight + 40f : 60f));
            drawOrderTestHeader(section, false);

            if (hasLab) {
                drawLabTable(section);
            }
            if (section.radiologyResults() != null) {
                for (RadiologyResult result : section.radiologyResults()) {
                    drawRadiologyResult(section, result);
                }
            }
        }

        private void drawOrderTestHeader(OrderTestSection section, boolean continued) {
            pdf.fillRect(margin, y - testHeaderHeight, contentWidth, testHeaderHeight, testHeaderBg);
            pdf.fillRect(margin, y - testHeaderHeight, 4f, testHeaderHeight, accentColor);

            float baseline = y - testHeaderHeight / 2 - 3.5f;
            String received = section.receivedDepartment() == null || section.receivedDepartment().isBlank()
                    ? null
                    : "Received Department: " + section.receivedDepartment().trim();
            float receivedWidth = received == null ? 0f : pdf.textWidth(received, Font.BOLD, 8);
            if (received != null) {
                pdf.text(margin + contentWidth - 12 - receivedWidth, baseline, Font.BOLD, 8, accentTextColor, received);
            }

            String title = "ORDER TEST: " + valueOrDash(section.testName()).toUpperCase(Locale.ROOT)
                    + (continued ? " (CONTINUED)" : "");
            float titleMax = contentWidth - 30 - (received == null ? 0f : receivedWidth + 16);
            pdf.text(margin + 16, baseline, Font.BOLD, 10.5f, accentTextColor, fit(title, Font.BOLD, 10.5f, titleMax));

            y -= testHeaderHeight + 10;
        }

        // ---------------------------------------------------------------- lab table

        private void drawLabTable(OrderTestSection section) {
            beginBox(() -> drawOrderTestHeader(section, true), this::drawLabTableHeader);
            drawLabTableHeader();

            List<LabResultRow> rows = section.labResults();
            for (int i = 0; i < rows.size(); i++) {
                drawLabRow(rows.get(i), i > 0);
            }

            endBox();
            y -= 12;
        }

        private void drawLabTableHeader() {
            float x = boxX;
            for (int i = 0; i < labHeaders.length; i++) {
                pdf.text(x + cellPaddingX, y - 15, Font.BOLD, 7.5f, pillText, labHeaders[i]);
                x += labWidths[i];
            }
            y -= tableHeaderHeight;
            pdf.line(boxX, y, boxX + boxWidth, y, 0.8f, cardBorder);
        }

        private void drawLabRow(LabResultRow row, boolean drawDivider) {
            float nameSize = 9f;
            float valueSize = 9f;
            float nameLineHeight = nameSize + 2.5f;
            float valueLineHeight = valueSize + 2.5f;

            List<String> nameLines = cap(pdf.wrap(valueOrDash(row.testName()), Font.BOLD, nameSize,
                    labWidths[0] - 2 * cellPaddingX));
            String category = blankToNull(row.category());

            Font resultFont = row.abnormal() ? Font.BOLD : Font.REGULAR;
            List<String> resultLines = cap(pdf.wrap(valueOrDash(row.result()), resultFont, valueSize,
                    labWidths[1] - 2 * cellPaddingX));
            String flag = row.abnormal() ? blankToNull(row.flag()) : null;

            List<String> unitLines = cap(pdf.wrap(valueOrDash(row.unit()), Font.REGULAR, valueSize,
                    labWidths[2] - 2 * cellPaddingX));
            List<String> rangeLines = cap(pdf.wrap(valueOrDash(row.normalRange()), Font.REGULAR, valueSize,
                    labWidths[3] - 2 * cellPaddingX));
            List<String> dateLines = cap(pdf.wrap(formatDateTime(row.resultDate()), Font.REGULAR, 8.5f,
                    labWidths[4] - 2 * cellPaddingX));

            float nameHeight = nameLines.size() * nameLineHeight + (category == null ? 0f : pillHeight + 4f);
            float resultHeight = resultLines.size() * valueLineHeight + (flag == null ? 0f : pillHeight + 4f);
            float contentHeight = Math.max(Math.max(nameHeight, resultHeight), Math.max(
                    Math.max(unitLines.size(), rangeLines.size()) * valueLineHeight,
                    dateLines.size() * (8.5f + 2.5f)));
            float rowHeight = contentHeight + 2 * cellPaddingY;

            float before = y;
            reserve(rowHeight);
            if (drawDivider && y == before) {
                pdf.line(boxX, y, boxX + boxWidth, y, 0.6f, dividerColor);
            }

            float top = y - cellPaddingY;
            float x = boxX;

            float textY = top - nameSize;
            for (String line : nameLines) {
                pdf.text(x + cellPaddingX, textY, Font.BOLD, nameSize, textColor, line);
                textY -= nameLineHeight;
            }
            if (category != null) {
                drawPill(x + cellPaddingX, textY + nameLineHeight - 4f,
                        category, labWidths[0] - 2 * cellPaddingX, pillBg, pillBorder, pillText);
            }
            x += labWidths[0];

            textY = top - valueSize;
            Color resultColor = row.abnormal() ? abnormalColor : textColor;
            for (String line : resultLines) {
                pdf.text(x + cellPaddingX, textY, resultFont, valueSize, resultColor, line);
                textY -= valueLineHeight;
            }
            if (flag != null) {
                drawPill(x + cellPaddingX, textY + valueLineHeight - 4f,
                        flag.toUpperCase(Locale.ROOT), labWidths[1] - 2 * cellPaddingX,
                        abnormalBg, abnormalBorder, abnormalColor);
            }
            x += labWidths[1];

            drawLines(x, top, unitLines, Font.REGULAR, valueSize, textColor);
            x += labWidths[2];
            drawLines(x, top, rangeLines, Font.REGULAR, valueSize, textColor);
            x += labWidths[3];
            drawLines(x, top, dateLines, Font.REGULAR, 8.5f, textColor);

            y -= rowHeight;
        }

        private void drawLines(float x, float top, List<String> lines, Font font, float size, Color color) {
            float textY = top - size;
            for (String line : lines) {
                pdf.text(x + cellPaddingX, textY, font, size, color, line);
                textY -= size + 2.5f;
            }
        }

        private void drawPill(float x, float topY, String label, float maxWidth,
                              Color fill, Color border, Color textColor) {
            float size = 6.5f;
            String text = fit(label, Font.BOLD, size, maxWidth - 12);
            float width = pdf.textWidth(text, Font.BOLD, size) + 12;
            float bottom = topY - pillHeight;
            pdf.roundedRect(x, bottom, width, pillHeight, pillHeight / 2, fill, border, 0.6f);
            pdf.text(x + 6, bottom + 3.8f, Font.BOLD, size, textColor, text);
        }

        // ---------------------------------------------------------------- radiology

        private void drawRadiologyResult(OrderTestSection section, RadiologyResult result) {
            reserve(60f);
            beginBox(() -> drawOrderTestHeader(section, true), () -> y -= 10);
            y -= 12;

            float x = boxX + cellPaddingX;
            float metaBaseline = y - 9;
            x = drawMeta(x, metaBaseline, "STATUS", result.status());
            x = drawMeta(x, metaBaseline, "SEVERITY", result.severity());
            drawMeta(x, metaBaseline, "REPORT DATE", formatDateTime(result.reportDate()));
            y -= 18;
            pdf.line(boxX + cellPaddingX, y, boxX + boxWidth - cellPaddingX, y, 0.6f, dividerColor);
            y -= 10;

            drawLabeledParagraph("REPORT", result.report(), accentTextColor);
            drawLabeledParagraph("CRITICAL FINDINGS", result.criticalFindings(), abnormalColor);
            drawLabeledParagraph("RADIOLOGIST COMMENTS", result.radiologistComments(), accentTextColor);
            drawLabeledParagraph("RADIOLOGIST INFORMATION", result.radiologistInformation(), accentTextColor);

            if (blankToNull(result.reviewedBy()) != null || blankToNull(result.approvedBy()) != null) {
                reserve(22f);
                float reviewX = boxX + cellPaddingX;
                float reviewBaseline = y - 9;
                reviewX = drawMeta(reviewX, reviewBaseline, "REVIEWED BY", result.reviewedBy());
                drawMeta(reviewX, reviewBaseline, "APPROVED BY", result.approvedBy());
                y -= 18;
            }

            y -= 2;
            endBox();
            y -= 12;
        }

        private float drawMeta(float x, float baseline, String label, String value) {
            String labelText = label + ":";
            String valueText = valueOrDash(value);
            pdf.text(x, baseline, Font.BOLD, 7.5f, labelColor, labelText);
            float valueX = x + pdf.textWidth(labelText, Font.BOLD, 7.5f) + 4;
            pdf.text(valueX, baseline, Font.BOLD, 9, textColor, valueText);
            return valueX + pdf.textWidth(valueText, Font.BOLD, 9) + 22;
        }

        private void drawLabeledParagraph(String label, String value, Color labelColor) {
            if (value == null || value.isBlank()) {
                return;
            }
            reserve(30f);
            pdf.text(boxX + cellPaddingX, y - 8, Font.BOLD, 7.5f, labelColor, label);
            y -= 14;

            float size = 9f;
            float lineHeight = size + 3.5f;
            for (String line : pdf.wrap(value, Font.REGULAR, size, boxWidth - 2 * cellPaddingX)) {
                reserve(lineHeight);
                pdf.text(boxX + cellPaddingX, y - size, Font.REGULAR, size, textColor, line);
                y -= lineHeight;
            }
            y -= 8;
        }

        // ---------------------------------------------------------------- footer

        private void drawFooters() {
            int total = pdf.pageCount();
            String patient = "Patient: " + valueOrDash(report.patientName())
                    + "   |   MRN: " + valueOrDash(report.medicalRecordNumber())
                    + "   |   Encounter: " + valueOrDash(report.encounterNumber());

            for (int i = 0; i < total; i++) {
                pdf.selectPage(i);
                pdf.line(margin, 42, margin + contentWidth, 42, 0.6f, dividerColor);
                pdf.text(margin, 30, Font.REGULAR, 7.5f, mutedColor, fit(patient, Font.REGULAR, 7.5f, contentWidth - 80));

                String page = "Page " + (i + 1) + " of " + total;
                float pageWidth = pdf.textWidth(page, Font.REGULAR, 7.5f);
                pdf.text(margin + contentWidth - pageWidth, 30, Font.REGULAR, 7.5f, mutedColor, page);

                if (truncated && i == total - 1) {
                    pdf.text(margin, 19, Font.BOLD, 7.5f, abnormalColor,
                            "Report truncated to respect the attachment size limit.");
                }
            }
        }

        // ---------------------------------------------------------------- helpers

        private List<String> cap(List<String> lines) {
            if (lines.isEmpty()) {
                return List.of("-");
            }
            if (lines.size() <= maxLinesPerCell) {
                return lines;
            }
            List<String> capped = new ArrayList<>(lines.subList(0, maxLinesPerCell));
            capped.set(maxLinesPerCell - 1, capped.get(maxLinesPerCell - 1) + " ...");
            return capped;
        }

        private String fit(String value, Font font, float size, float maxWidth) {
            if (pdf.textWidth(value, font, size) <= maxWidth) {
                return value;
            }
            String ellipsis = "...";
            int end = value.length();
            while (end > 0 && pdf.textWidth(value.substring(0, end) + ellipsis, font, size) > maxWidth) {
                end--;
            }
            return value.substring(0, end) + ellipsis;
        }

        private String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }

        private String valueOrDash(String value) {
            return value == null || value.isBlank() ? "-" : value.trim();
        }

        private String formatDate(LocalDate date) {
            return date == null ? "-" : dateFormat.format(date);
        }

        private String formatDateTime(Instant instant) {
            return instant == null ? "-" : dateTimeFormat.format(instant.atZone(reportZone));
        }
    }
}
