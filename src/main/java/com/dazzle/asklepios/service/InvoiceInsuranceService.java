package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.integration.waseel.dto.InsuranceCoverage;
import com.dazzle.asklepios.integration.waseel.service.WaseelCoverageExtractionService;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceInsuranceService {

    private final FinancialDocumentRepository documentRepo;
    private final FinancialDocumentItemRepository itemRepo;
    private final InsuranceCalculationService insuranceCalculationService;
    private final WaseelCoverageExtractionService coverageExtractionService;
    private final WaseelEligibilityRequestRepository waseelRepo;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public void applyInsurance(Long documentId) {

        // ✅ 1. load document
        FinancialDocument document = documentRepo.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Document not found"));

        // ✅ 2. lock check
        if (!document.isEditable()) {
            throw new IllegalStateException("Invoice is locked (POSTED)");
        }

        // ✅ 3. load items
        List<FinancialDocumentItem> items =
                itemRepo.findByDocument_Id(documentId);

        // ✅ 4. load coverage
        InsuranceCoverage coverage =
                loadCoverage(document.getPatientId());

        // ✅ 5. loop items
        for (FinancialDocumentItem item : items) {

            BigDecimal net = safe(item.getNetAmount());

            // ✅ CASH case
            if (coverage == null) {
                item.setPatientShareAmount(net);
                item.setInsuranceShareAmount(ZERO);
                continue;
            }

            // ✅ INSURANCE case
            InsuranceSplit split =
                    insuranceCalculationService.calculateSplit(
                            net,
                            coverage.getCopaymentPercent(),
                            coverage.getCopaymentCap()
                    );

            item.setPatientShareAmount(split.patientShare());
            item.setInsuranceShareAmount(split.insuranceShare());
        }

        itemRepo.saveAll(items);
    }

    private InsuranceCoverage loadCoverage(Long patientId) {

        return waseelRepo
                .findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
                        patientId, "SUCCESS"
                )
                .map(r -> coverageExtractionService.extractCoverage(r.getResponseJson()))
                .orElse(null);
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? ZERO : v;
    }
}
