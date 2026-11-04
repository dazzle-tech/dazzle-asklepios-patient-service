package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Patient invoice lines are the collected amount. A debit posted at checkout
 * is still outstanding on the invoice and is not patient paid.
 */
@Service
@Transactional(readOnly = true)
public class EncounterInvoicePaymentReader {

    private static final List<FinancialDocumentStatus> INVOICE_STATUSES = List.of(
            FinancialDocumentStatus.ISSUED,
            FinancialDocumentStatus.PARTIALLY_PAID,
            FinancialDocumentStatus.PAID,
            FinancialDocumentStatus.POSTED
    );

    private final FinancialDocumentRepository financialDocumentRepository;
    private final FinancialDocumentItemRepository financialDocumentItemRepository;

    public EncounterInvoicePaymentReader(
            FinancialDocumentRepository financialDocumentRepository,
            FinancialDocumentItemRepository financialDocumentItemRepository
    ) {
        this.financialDocumentRepository = financialDocumentRepository;
        this.financialDocumentItemRepository = financialDocumentItemRepository;
    }

    public Map<Long, List<FinancialDocumentItem>> patientInvoiceItems(
            Collection<Long> encounterIds
    ) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Map.of();
        }

        List<FinancialDocument> invoices =
                financialDocumentRepository
                        .findAllByEncounterIdInAndDocumentTypeAndDocumentSubtypeAndStatusIn(
                                encounterIds,
                                FinancialDocumentType.INVOICE,
                                FinancialDocumentSubtype.PATIENT,
                                INVOICE_STATUSES
                        );

        Map<Long, FinancialDocument> latestInvoiceByEncounter = new HashMap<>();
        for (FinancialDocument invoice : invoices) {
            if (invoice.getEncounterId() == null || invoice.getId() == null) {
                continue;
            }

            FinancialDocument current =
                    latestInvoiceByEncounter.get(invoice.getEncounterId());
            if (current == null || invoice.getId() > current.getId()) {
                latestInvoiceByEncounter.put(invoice.getEncounterId(), invoice);
            }
        }

        if (latestInvoiceByEncounter.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> encounterIdByDocumentId = new HashMap<>();
        for (FinancialDocument invoice : latestInvoiceByEncounter.values()) {
            encounterIdByDocumentId.put(invoice.getId(), invoice.getEncounterId());
        }

        Map<Long, List<FinancialDocumentItem>> itemsByEncounter = new HashMap<>();
        List<FinancialDocumentItem> items =
                financialDocumentItemRepository.findByDocument_IdIn(
                        encounterIdByDocumentId.keySet()
                );

        for (FinancialDocumentItem item : items) {
            if (item.getDocument() == null || item.getDocument().getId() == null) {
                continue;
            }

            Long encounterId =
                    encounterIdByDocumentId.get(item.getDocument().getId());
            if (encounterId == null) {
                continue;
            }

            itemsByEncounter
                    .computeIfAbsent(encounterId, ignored -> new ArrayList<>())
                    .add(item);
        }

        return itemsByEncounter;
    }
}
