package com.dazzle.asklepios.integration.waseel.service.pdf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.Deflater;

/**
 * Minimal PDF 1.4 writer (A4, standard Helvetica fonts, WinAnsi encoding).
 * Characters outside Latin-1 cannot be rendered by the standard fonts and are written as '?'.
 */
final class SimplePdfWriter {

    final float pageWidth = 595.28f;
    final float pageHeight = 841.89f;

    enum Font {
        REGULAR("F1", new int[] {
                278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
                556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
                1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
                667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
                333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
                556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584
        }),
        BOLD("F2", new int[] {
                278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278,
                556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611,
                975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778,
                667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556,
                333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611,
                611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584
        });

        private final String resourceName;
        private final int[] widths;

        Font(String resourceName, int[] widths) {
            this.resourceName = resourceName;
            this.widths = widths;
        }
    }

    record Color(float r, float g, float b) {
        Color(int hex) {
            this(((hex >> 16) & 0xFF) / 255f, ((hex >> 8) & 0xFF) / 255f, (hex & 0xFF) / 255f);
        }

        private String operands() {
            return format(r) + " " + format(g) + " " + format(b);
        }

        private String format(float value) {
            if (value == Math.rint(value)) {
                return String.valueOf((long) value);
            }
            return String.format(Locale.ROOT, "%.2f", value);
        }
    }

    final class PageLimitReachedException extends RuntimeException {
        PageLimitReachedException() {
            super(null, null, false, false);
        }
    }

    private final int maxPages;
    private final List<ByteArrayOutputStream> pages = new ArrayList<>();
    private ByteArrayOutputStream current;

    SimplePdfWriter(int maxPages) {
        this.maxPages = maxPages;
    }

    int pageCount() {
        return pages.size();
    }

    void newPage() {
        if (pages.size() >= maxPages) {
            throw new PageLimitReachedException();
        }
        current = new ByteArrayOutputStream();
        pages.add(current);
    }

    void selectPage(int index) {
        current = pages.get(index);
    }

    void text(float x, float y, Font font, float size, Color color, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        write(color.operands() + " rg BT /" + font.resourceName + " " + num(size) + " Tf "
                + num(x) + " " + num(y) + " Td (");
        writeEncoded(value);
        write(") Tj ET 0 g\n");
    }

    void fillRect(float x, float y, float width, float height, Color color) {
        write(color.operands() + " rg " + num(x) + " " + num(y) + " " + num(width) + " " + num(height) + " re f 0 g\n");
    }

    void strokeRect(float x, float y, float width, float height, float lineWidth, Color color) {
        write(color.operands() + " RG " + num(lineWidth) + " w "
                + num(x) + " " + num(y) + " " + num(width) + " " + num(height) + " re S 0 G\n");
    }

    /**
     * Draws a rounded rectangle; {@code fill} and/or {@code stroke} may be {@code null}.
     */
    void roundedRect(float x, float y, float width, float height, float radius,
                     Color fill, Color stroke, float lineWidth) {
        if (fill == null && stroke == null) {
            return;
        }
        float r = Math.max(0f, Math.min(radius, Math.min(width, height) / 2f));
        float k = r * 0.5523f;
        float right = x + width;
        float top = y + height;

        StringBuilder path = new StringBuilder();
        path.append(num(x + r)).append(' ').append(num(y)).append(" m ");
        path.append(num(right - r)).append(' ').append(num(y)).append(" l ");
        path.append(num(right - r + k)).append(' ').append(num(y)).append(' ')
                .append(num(right)).append(' ').append(num(y + r - k)).append(' ')
                .append(num(right)).append(' ').append(num(y + r)).append(" c ");
        path.append(num(right)).append(' ').append(num(top - r)).append(" l ");
        path.append(num(right)).append(' ').append(num(top - r + k)).append(' ')
                .append(num(right - r + k)).append(' ').append(num(top)).append(' ')
                .append(num(right - r)).append(' ').append(num(top)).append(" c ");
        path.append(num(x + r)).append(' ').append(num(top)).append(" l ");
        path.append(num(x + r - k)).append(' ').append(num(top)).append(' ')
                .append(num(x)).append(' ').append(num(top - r + k)).append(' ')
                .append(num(x)).append(' ').append(num(top - r)).append(" c ");
        path.append(num(x)).append(' ').append(num(y + r)).append(" l ");
        path.append(num(x)).append(' ').append(num(y + r - k)).append(' ')
                .append(num(x + r - k)).append(' ').append(num(y)).append(' ')
                .append(num(x + r)).append(' ').append(num(y)).append(" c h ");

        StringBuilder ops = new StringBuilder();
        if (fill != null) {
            ops.append(fill.operands()).append(" rg ");
        }
        if (stroke != null) {
            ops.append(stroke.operands()).append(" RG ").append(num(lineWidth)).append(" w ");
        }
        String paint = fill != null && stroke != null ? "B" : fill != null ? "f" : "S";
        write(ops + path.toString() + paint + " 0 g 0 G\n");
    }

    void line(float x1, float y1, float x2, float y2, float lineWidth, Color color) {
        write(color.operands() + " RG " + num(lineWidth) + " w "
                + num(x1) + " " + num(y1) + " m " + num(x2) + " " + num(y2) + " l S 0 G\n");
    }

    void dashedLine(float x1, float y1, float x2, float y2, float lineWidth, Color color, float dash) {
        write("[" + num(dash) + " " + num(dash) + "] 0 d ");
        line(x1, y1, x2, y2, lineWidth, color);
        write("[] 0 d\n");
    }

    float textWidth(String value, Font font, float size) {
        if (value == null || value.isEmpty()) {
            return 0f;
        }
        long units = 0;
        for (int i = 0; i < value.length(); i++) {
            units += charWidth(toWinAnsi(value.charAt(i)), font);
        }
        return units * size / 1000f;
    }

    /**
     * Wraps text to the given width. Explicit line breaks are preserved; words longer than the width are split.
     */
    List<String> wrap(String value, Font font, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return lines;
        }

        for (String paragraph : value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String trimmed = paragraph.strip();
            if (trimmed.isEmpty()) {
                if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) {
                    lines.add("");
                }
                continue;
            }

            StringBuilder line = new StringBuilder();
            for (String word : trimmed.split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (textWidth(candidate, font, size) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }

                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }

                String remaining = word;
                while (textWidth(remaining, font, size) > maxWidth && remaining.length() > 1) {
                    int cut = remaining.length() - 1;
                    while (cut > 1 && textWidth(remaining.substring(0, cut), font, size) > maxWidth) {
                        cut--;
                    }
                    lines.add(remaining.substring(0, cut));
                    remaining = remaining.substring(cut);
                }
                line.append(remaining);
            }

            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }

        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    byte[] toBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();

        int pageCount = pages.size();
        int firstPageObject = 5;

        writeAscii(out, "%PDF-1.4\n");
        out.write(new byte[]{'%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'}, 0, 6);

        offsets.add(out.size());
        writeAscii(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            kids.append(firstPageObject + i * 2).append(" 0 R ");
        }
        offsets.add(out.size());
        writeAscii(out, "2 0 obj\n<< /Type /Pages /Kids [" + kids.toString().trim() + "] /Count " + pageCount + " >>\nendobj\n");

        offsets.add(out.size());
        writeAscii(out, "3 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n");

        offsets.add(out.size());
        writeAscii(out, "4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n");

        for (int i = 0; i < pageCount; i++) {
            int pageObject = firstPageObject + i * 2;
            int contentObject = pageObject + 1;

            offsets.add(out.size());
            writeAscii(out, pageObject + " 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                    + num(pageWidth) + " " + num(pageHeight) + "] "
                    + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> "
                    + "/Contents " + contentObject + " 0 R >>\nendobj\n");

            byte[] compressed = deflate(pages.get(i).toByteArray());
            offsets.add(out.size());
            writeAscii(out, contentObject + " 0 obj\n<< /Length " + compressed.length + " /Filter /FlateDecode >>\nstream\n");
            out.write(compressed, 0, compressed.length);
            writeAscii(out, "\nendstream\nendobj\n");
        }

        int xrefOffset = out.size();
        int objectCount = offsets.size() + 1;
        StringBuilder xref = new StringBuilder();
        xref.append("xref\n0 ").append(objectCount).append('\n');
        xref.append("0000000000 65535 f \n");
        for (Integer offset : offsets) {
            xref.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        xref.append("trailer\n<< /Size ").append(objectCount).append(" /Root 1 0 R >>\n");
        xref.append("startxref\n").append(xrefOffset).append("\n%%EOF\n");
        writeAscii(out, xref.toString());

        return out.toByteArray();
    }

    private void write(String ascii) {
        writeAscii(current, ascii);
    }

    private void writeEncoded(String value) {
        for (int i = 0; i < value.length(); i++) {
            int code = toWinAnsi(value.charAt(i));
            if (code == '(' || code == ')' || code == '\\') {
                current.write('\\');
            }
            current.write(code);
        }
    }

    private int toWinAnsi(char c) {
        if (c == '\t') {
            return ' ';
        }
        if ((c >= 32 && c <= 126) || (c >= 160 && c <= 255)) {
            return c;
        }
        return switch (c) {
            case '\u2018', '\u2019' -> '\'';
            case '\u201C', '\u201D' -> '"';
            case '\u2013', '\u2014' -> '-';
            case '\u2022' -> 149;
            case '\u2026' -> 133;
            case '\u2264' -> '<';
            case '\u2265' -> '>';
            default -> '?';
        };
    }

    private int charWidth(int code, Font font) {
        if (code >= 32 && code <= 126) {
            return font.widths[code - 32];
        }
        return 556;
    }

    private byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, input.length / 2));
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private void writeAscii(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        out.write(bytes, 0, bytes.length);
    }

    private String num(float value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
