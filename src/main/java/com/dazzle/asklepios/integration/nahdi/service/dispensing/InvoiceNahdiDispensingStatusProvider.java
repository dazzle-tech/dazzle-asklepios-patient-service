package com.dazzle.asklepios.integration.nahdi.service.dispensing;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.integration.nahdi.client.NahdiClient;
import com.dazzle.asklepios.integration.nahdi.config.NahdiProperties;
import com.dazzle.asklepios.integration.nahdi.dto.dispensing.NahdiDispensingStatus;
import com.dazzle.asklepios.integration.nahdi.dto.invoice.NahdiSalesInvoicesResponse;
import com.dazzle.asklepios.integration.nahdi.dto.invoice.NahdiSalesInvoicesResponse.Invoice;
import com.dazzle.asklepios.integration.nahdi.dto.invoice.NahdiSalesInvoicesResponse.Item;
import com.dazzle.asklepios.integration.nahdi.mapper.NahdiPrescriptionLines;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispensing status taken from Nahdi sales invoices (GetSalesInvoices), enabled with
 * {@code nahdi.dispensing.source=INVOICE}.
 * <p>
 * Confirmed by Nahdi:
 * <ul>
 *   <li>An invoice belongs to our prescription through its reference ({@code Provider_Referance}), which is the
 *       {@code pres_RefID} we send. Invoices with another reference are ignored.</li>
 *   <li>An invoice item is our prescription line with the same line number ({@code Item_LineNo} = {@code line_No}),
 *       so a substitute product still maps to its line.</li>
 *   <li>A dispensed medication appears on a sale invoice; {@code Item_Status} is not relevant.</li>
 * </ul>
 * A returned medication counts as not dispensed (it becomes editable again).
 * <p>
 * Still to confirm: the {@code Invoice_Type} value of a return (configurable, {@code RETURN} by default), and
 * whether one prescription's lines can be dispensed over several invoices (supported: invoices are applied in
 * time order and the latest one decides each line's status). A line is always dispensed as a whole.
 */
@Component
@ConditionalOnProperty(name = "nahdi.dispensing.source", havingValue = "INVOICE")
public class InvoiceNahdiDispensingStatusProvider implements NahdiDispensingStatusProvider {

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceNahdiDispensingStatusProvider.class);

    private static final ZoneId NAHDI_ZONE = ZoneId.of("Asia/Riyadh");
    private static final DateTimeFormatter INVOICE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NahdiProperties nahdiProperties;
    private final NahdiClient nahdiClient;
    // The response has fields we do not map, so unknown properties are ignored for this call only.
    private final ObjectMapper invoiceReader;

    public InvoiceNahdiDispensingStatusProvider(NahdiProperties nahdiProperties, NahdiClient nahdiClient, ObjectMapper objectMapper) {
        this.nahdiProperties = nahdiProperties;
        this.nahdiClient = nahdiClient;
        this.invoiceReader = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public NahdiDispensingStatus fetch(PatientPrescription prescription) {
        String prescriptionRef = NahdiPrescriptionLines.reference(prescription);

        NahdiSalesInvoicesResponse response;
        try {
            String raw = nahdiClient.getSalesInvoices(prescriptionRef);
            LOG.debug("[NAHDI][INVOICES] prescriptionRef={} response={}", prescriptionRef, raw);
            response = raw == null ? null : invoiceReader.readValue(raw, NahdiSalesInvoicesResponse.class);
        } catch (Exception e) {
            LOG.error("[NAHDI][INVOICES] GetSalesInvoices failed. prescriptionRef={}", prescriptionRef, e);
            return NahdiDispensingStatus.unavailable("GetSalesInvoices failed: " + e.getMessage());
        }
        return toStatus(prescription, prescriptionRef, response);
    }

    /** Maps a GetSalesInvoices response to the status of each sent line. */
    NahdiDispensingStatus toStatus(PatientPrescription prescription, String prescriptionRef, NahdiSalesInvoicesResponse response) {
        if (response == null || !Boolean.TRUE.equals(response.success())) {
            String error = response == null || response.error() == null ? "no response" : response.error().toString();
            LOG.warn("[NAHDI][INVOICES] GetSalesInvoices was not successful. prescriptionRef={}, error={}", prescriptionRef, error);
            return NahdiDispensingStatus.unavailable("GetSalesInvoices was not successful: " + error);
        }

        // line_No -> medication, numbered exactly as when the prescription was sent.
        List<PatientPrescriptionMedication> lines = NahdiPrescriptionLines.sentLines(prescription);
        Map<Integer, PatientPrescriptionMedication> byLineNo = new HashMap<>();
        Map<Long, LineState> states = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            byLineNo.put(i + 1, lines.get(i));
            states.put(lines.get(i).getId(), new LineState());
        }

        List<Invoice> invoices = new ArrayList<>(response.invoices() == null ? List.of() : response.invoices());
        invoices.removeIf(invoice -> invoice == null || invoice.head() == null);
        invoices.sort(Comparator.comparing(InvoiceNahdiDispensingStatusProvider::invoiceInstant,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        String saleType = nahdiProperties.dispensing().saleInvoiceType();
        String returnType = nahdiProperties.dispensing().returnInvoiceType();
        int applied = 0;
        for (Invoice invoice : invoices) {
            String invoiceNo = invoice.head().invoiceNo();
            String reference = invoice.head().providerReference();
            // The request is already filtered by our reference, so an invoice without one is kept.
            if (StringUtils.hasText(reference) && !reference.trim().equalsIgnoreCase(prescriptionRef)) {
                LOG.warn("[NAHDI][INVOICES] invoice of another prescription ignored. prescriptionRef={}, Provider_Referance={}, invoiceNo={}",
                        prescriptionRef, reference, invoiceNo);
                continue;
            }
            String type = invoice.head().invoiceType();
            boolean sale = type != null && type.trim().equalsIgnoreCase(saleType);
            boolean returned = type != null && type.trim().equalsIgnoreCase(returnType);
            if (!sale && !returned) {
                LOG.warn("[NAHDI][INVOICES] unknown Invoice_Type={} ignored. prescriptionRef={}, invoiceNo={}", type, prescriptionRef, invoiceNo);
                continue;
            }
            applied++;
            Instant at = invoiceInstant(invoice);
            for (Item item : invoice.items() == null ? List.<Item>of() : invoice.items()) {
                PatientPrescriptionMedication medication = byLineNo.get(lineNumber(item.lineNo()));
                if (medication == null) {
                    LOG.warn("[NAHDI][INVOICES] Item_LineNo={} matches no sent line. prescriptionRef={}, invoiceNo={}, description={}",
                            item.lineNo(), prescriptionRef, invoiceNo, item.description());
                    continue;
                }
                LineState state = states.get(medication.getId());
                state.dispensed = sale;
                state.dispensedAt = sale ? at : null;
                state.reference = sale ? invoiceNo : null;
            }
        }

        // Every sent line is reported, so a returned or never-dispensed medication is stored as not dispensed.
        List<NahdiDispensingStatus.Line> result = lines.stream()
                .map(m -> {
                    LineState state = states.get(m.getId());
                    return new NahdiDispensingStatus.Line(m.getId(), state.dispensed, state.dispensedAt, state.reference);
                })
                .toList();
        LOG.info("[NAHDI][INVOICES] prescriptionRef={} invoices={} applied={} dispensedLines={}", prescriptionRef, invoices.size(), applied,
                result.stream().filter(NahdiDispensingStatus.Line::dispensed).count());
        return NahdiDispensingStatus.of(result);
    }

    private static final class LineState {
        private boolean dispensed;
        private Instant dispensedAt;
        private String reference;
    }

    private static Integer lineNumber(String lineNo) {
        if (!StringUtils.hasText(lineNo)) {
            return null;
        }
        try {
            return Integer.valueOf(lineNo.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant invoiceInstant(Invoice invoice) {
        String time = invoice.head().invoiceTime();
        try {
            if (StringUtils.hasText(time)) {
                return LocalDateTime.parse(time.trim(), INVOICE_TIME).atZone(NAHDI_ZONE).toInstant();
            }
            if (StringUtils.hasText(invoice.head().invoiceDate())) {
                return LocalDate.parse(invoice.head().invoiceDate().trim()).atStartOfDay(NAHDI_ZONE).toInstant();
            }
        } catch (Exception e) {
            LOG.warn("[NAHDI][INVOICES] unreadable invoice time '{}' (invoiceNo={})", time, invoice.head().invoiceNo());
        }
        return null;
    }
}
