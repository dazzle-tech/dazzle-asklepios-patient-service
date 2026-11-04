package com.dazzle.asklepios.integration.waseel.service.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dazzle.asklepios.integration.waseel.service.pdf.InvestigationResultsPdfRenderer.Attachment;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class InvestigationResultsPdfRendererTest {

    private final InvestigationResultsPdfRenderer renderer = new InvestigationResultsPdfRenderer();

    @Test
    void keepsPdfWhenWithinOneMegabyte() {
        byte[] pdf = renderer.render(sampleReport());

        Attachment attachment = renderer.attachmentFor(pdf);

        assertTrue(renderer.withinSizeLimit(pdf));
        assertEquals("investigation-results.pdf", attachment.filename());
        assertEquals("application/pdf", attachment.contentType());
        assertArrayEquals(pdf, attachment.content());
    }

    @Test
    void zipsPdfWhenLargerThanOneMegabyte() throws Exception {
        byte[] pdf = new byte[1_000_001];
        pdf[0] = '%';
        pdf[1] = 'P';
        pdf[2] = 'D';
        pdf[3] = 'F';

        Attachment attachment = renderer.attachmentFor(pdf);

        assertEquals("investigation-results.zip", attachment.filename());
        assertEquals("application/zip", attachment.contentType());
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(attachment.content()))) {
            assertEquals("investigation-results.pdf", zip.getNextEntry().getName());
            assertArrayEquals(pdf, zip.readAllBytes());
        }
    }

    private InvestigationResultsReport sampleReport() {
        return new InvestigationResultsReport(
                "Patient",
                "MRN1",
                LocalDate.of(1990, 1, 1),
                "Male",
                "E1",
                LocalDate.of(2026, 1, 1),
                List.of(new InvestigationResultsReport.OrderSection(
                        "ORD1",
                        "E1",
                        "Lab",
                        List.of(new InvestigationResultsReport.OrderTestSection(
                                "CBC",
                                "Hematology",
                                List.of(new InvestigationResultsReport.LabResultRow(
                                        "Hemoglobin",
                                        "Hematology",
                                        "13.2",
                                        "g/dL",
                                        "12-16",
                                        null,
                                        false,
                                        Instant.parse("2026-01-01T08:00:00Z")
                                )),
                                List.of()
                        ))
                ))
        );
    }
}
