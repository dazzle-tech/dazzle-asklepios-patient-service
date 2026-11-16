package com.dazzle.asklepios.integration.nahdi.service.dispensing;

import com.dazzle.asklepios.client.setup.BrandMedicationClient;
import com.dazzle.asklepios.client.setup.PharmaceuticalClient;
import com.dazzle.asklepios.client.setup.dto.BrandMedicationSetupDTO;
import com.dazzle.asklepios.client.setup.dto.PharmaceuticalDTO;
import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.integration.nahdi.client.NahdiClient;
import com.dazzle.asklepios.integration.nahdi.config.NahdiProperties;
import com.dazzle.asklepios.integration.nahdi.dto.dispensing.NahdiDispensingStatus;
import com.dazzle.asklepios.integration.nahdi.dto.invoice.NahdiSalesInvoicesResponse;
import com.dazzle.asklepios.integration.nahdi.dto.invoice.NahdiSalesInvoicesResponse.Invoice;
import com.dazzle.asklepios.integration.nahdi.dto.invoice.NahdiSalesInvoicesResponse.Item;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Dispensing status taken from Nahdi sales invoices (GetSalesInvoices), enabled with
 * {@code nahdi.dispensing.source=INVOICE}.
 * <p>
 * Assumptions until Nahdi confirms them (see the open questions):
 * <ul>
 *   <li>The {@code PrescriptionID} filter is our {@code pres_RefID} (the prescription number, e.g. Rx-119).</li>
 *   <li>A medication on a sale invoice ({@code Invoice_Type} = sale type) was dispensed; on a return invoice
 *       it was returned. Invoices are applied in time order, so the latest one wins.</li>
 *   <li>An invoice item matches a prescription line by code: {@code Item_SFDA} = pharmaceutical Code (SFDA),
 *       or {@code Item_GTIN} = pharmaceutical Billing Code (NPHIES). A substitute with another code does not
 *       match and is only logged.</li>
 *   <li>{@code Item_Status} is not used yet (its values are unknown).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "nahdi.dispensing.source", havingValue = "INVOICE")
public class InvoiceNahdiDispensingStatusProvider implements NahdiDispensingStatusProvider {

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceNahdiDispensingStatusProvider.class);

    private static final ZoneId NAHDI_ZONE = ZoneId.of("Asia/Riyadh");
    private static final DateTimeFormatter INVOICE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NahdiProperties nahdiProperties;
    private final NahdiClient nahdiClient;
    private final PharmaceuticalClient pharmaceuticalClient;
    private final BrandMedicationClient brandMedicationClient;
    // The response has fields we do not map, so unknown properties are ignored for this call only.
    private final ObjectMapper invoiceReader;

    public InvoiceNahdiDispensingStatusProvider(
            NahdiProperties nahdiProperties,
            NahdiClient nahdiClient,
            PharmaceuticalClient pharmaceuticalClient,
            BrandMedicationClient brandMedicationClient,
            ObjectMapper objectMapper
    ) {
        this.nahdiProperties = nahdiProperties;
        this.nahdiClient = nahdiClient;
        this.pharmaceuticalClient = pharmaceuticalClient;
        this.brandMedicationClient = brandMedicationClient;
        this.invoiceReader = objectMapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public NahdiDispensingStatus fetch(PatientPrescription prescription) {
        String prescriptionRef = StringUtils.hasText(prescription.getPrescriptionNum())
                ? prescription.getPrescriptionNum()
                : String.valueOf(prescription.getId());

        NahdiSalesInvoicesResponse response;
        try {
            String raw = nahdiClient.getSalesInvoices(prescriptionRef);
            LOG.debug("[NAHDI][INVOICES] prescriptionRef={} response={}", prescriptionRef, raw);
            response = raw == null ? null : invoiceReader.readValue(raw, NahdiSalesInvoicesResponse.class);
        } catch (Exception e) {
            LOG.error("[NAHDI][INVOICES] GetSalesInvoices failed. prescriptionRef={}", prescriptionRef, e);
            return NahdiDispensingStatus.unavailable("GetSalesInvoices failed: " + e.getMessage());
        }
        if (response == null || !Boolean.TRUE.equals(response.success())) {
            String error = response == null || response.error() == null ? "no response" : response.error().toString();
            LOG.warn("[NAHDI][INVOICES] GetSalesInvoices was not successful. prescriptionRef={}, error={}", prescriptionRef, error);
            return NahdiDispensingStatus.unavailable("GetSalesInvoices was not successful: " + error);
        }

        List<PatientPrescriptionMedication> medications = prescription.getMedications() == null
                ? List.of()
                : prescription.getMedications().stream().filter(Objects::nonNull).toList();
        Map<String, List<PatientPrescriptionMedication>> medicationsByCode = indexByCode(medications);
        if (medicationsByCode == null) {
            // Without every line's codes a dispensed line could look not dispensed and be unlocked by mistake.
            return NahdiDispensingStatus.unavailable("Could not load the medication codes");
        }

        Map<Long, LineState> states = new HashMap<>();
        medications.forEach(m -> states.put(m.getId(), new LineState()));

        List<Invoice> invoices = response.invoices() == null ? List.of() : new ArrayList<>(response.invoices());
        invoices.removeIf(invoice -> invoice == null || invoice.head() == null);
        invoices.sort(Comparator.comparing(InvoiceNahdiDispensingStatusProvider::invoiceInstant,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        String saleType = nahdiProperties.dispensing().saleInvoiceType();
        String returnType = nahdiProperties.dispensing().returnInvoiceType();
        for (Invoice invoice : invoices) {
            String type = invoice.head().invoiceType();
            boolean sale = type != null && type.equalsIgnoreCase(saleType);
            boolean returned = type != null && type.equalsIgnoreCase(returnType);
            if (!sale && !returned) {
                LOG.warn("[NAHDI][INVOICES] unknown Invoice_Type={} ignored. prescriptionRef={}, invoiceNo={}",
                        type, prescriptionRef, invoice.head().invoiceNo());
                continue;
            }
            if (StringUtils.hasText(invoice.head().providerReference()) && !prescriptionRef.equals(invoice.head().providerReference())) {
                LOG.warn("[NAHDI][INVOICES] Provider_Referance={} differs from prescriptionRef={} (invoiceNo={})",
                        invoice.head().providerReference(), prescriptionRef, invoice.head().invoiceNo());
            }
            Instant at = invoiceInstant(invoice);
            for (Item item : invoice.items() == null ? List.<Item>of() : invoice.items()) {
                Set<PatientPrescriptionMedication> matched = match(item, medicationsByCode);
                if (matched.isEmpty()) {
                    LOG.warn("[NAHDI][INVOICES] invoice item matches no prescription line (substitute?). prescriptionRef={}, invoiceNo={}, sfda={}, gtin={}, description={}",
                            prescriptionRef, invoice.head().invoiceNo(), item.sfda(), item.gtin(), item.description());
                }
                for (PatientPrescriptionMedication medication : matched) {
                    LineState state = states.get(medication.getId());
                    state.dispensed = sale;
                    state.dispensedAt = sale ? at : null;
                    state.reference = sale ? invoice.head().invoiceNo() : null;
                }
            }
        }

        // Every line is reported, so a returned or never-dispensed medication is stored as not dispensed.
        List<NahdiDispensingStatus.Line> lines = medications.stream()
                .map(m -> {
                    LineState state = states.get(m.getId());
                    return new NahdiDispensingStatus.Line(m.getId(), state.dispensed, state.dispensedAt, state.reference);
                })
                .toList();
        LOG.info("[NAHDI][INVOICES] prescriptionRef={} invoices={} dispensedLines={}", prescriptionRef, invoices.size(),
                lines.stream().filter(NahdiDispensingStatus.Line::dispensed).count());
        return NahdiDispensingStatus.of(lines);
    }

    private static final class LineState {
        private boolean dispensed;
        private Instant dispensedAt;
        private String reference;
    }

    /**
     * Codes of each line: pharmaceutical Code (SFDA) and Billing Code (NPHIES / GTIN), or the brand code for older lines.
     * Returns null when a line's pharmaceutical or brand could not be loaded.
     */
    private Map<String, List<PatientPrescriptionMedication>> indexByCode(List<PatientPrescriptionMedication> medications) {
        Map<String, List<PatientPrescriptionMedication>> byCode = new HashMap<>();
        for (PatientPrescriptionMedication medication : medications) {
            List<String> codes = new ArrayList<>();
            if (medication.getPharmaceuticalId() != null) {
                PharmaceuticalDTO pharmaceutical = load(() -> pharmaceuticalClient.getPharmaceutical(medication.getPharmaceuticalId()),
                        "pharmaceutical " + medication.getPharmaceuticalId());
                if (pharmaceutical == null) {
                    return null;
                }
                codes.add(pharmaceutical.code());
                codes.add(pharmaceutical.billingCode());
            } else if (medication.getMedicationsId() != null) {
                BrandMedicationSetupDTO brand = load(() -> brandMedicationClient.getBrandMedication(medication.getMedicationsId()),
                        "brand medication " + medication.getMedicationsId());
                if (brand == null) {
                    return null;
                }
                codes.add(brand.code());
            }
            codes.stream()
                    .map(InvoiceNahdiDispensingStatusProvider::normalize)
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(code -> byCode.computeIfAbsent(code, k -> new ArrayList<>()).add(medication));
        }
        return byCode;
    }

    private static Set<PatientPrescriptionMedication> match(Item item, Map<String, List<PatientPrescriptionMedication>> byCode) {
        Set<PatientPrescriptionMedication> matched = new HashSet<>();
        for (String code : new String[] {item.sfda(), item.gtin()}) {
            String key = normalize(code);
            if (key != null) {
                matched.addAll(byCode.getOrDefault(key, List.of()));
            }
        }
        return matched;
    }

    /** Codes compared trimmed, upper case and without leading zeros (GTINs are often zero-padded). */
    private static String normalize(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String value = code.trim().toUpperCase();
        String stripped = value.replaceFirst("^0+(?=.)", "");
        return stripped.isEmpty() ? null : stripped;
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

    private static <T> T load(java.util.function.Supplier<T> supplier, String description) {
        try {
            return supplier.get();
        } catch (Exception e) {
            LOG.warn("[NAHDI][INVOICES] failed to load {}: {}", description, e.getMessage());
            return null;
        }
    }
}
