package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
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
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final InsurancePatientShareCalculator insurancePatientShareCalculator;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public void applyInsurance(Long documentId) {
        FinancialDocument document = documentRepo.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Document not found"));

        if (!document.isEditable()) {
            throw new IllegalStateException("Invoice is locked (POSTED)");
        }

        List<FinancialDocumentItem> items =
                itemRepo.findByDocument_Id(documentId);

        for (FinancialDocumentItem item : items) {
            BigDecimal net = safe(item.getNetAmount());

            PatientServiceAndProduct serviceProduct =
                    patientServiceAndProductRepository
                            .findById(item.getPatientServiceProductId())
                            .orElse(null);

            if (serviceProduct == null || serviceProduct.getPatientInsuranceId() == null) {
                item.setPatientShareAmount(net);
                item.setInsuranceShareAmount(ZERO);
                continue;
            }

            PatientInsurance insurance =
                    patientInsuranceRepository
                            .findByIdAndPatient_Id(
                                    serviceProduct.getPatientInsuranceId(),
                                    document.getPatientId()
                            )
                            .orElse(null);

            if (insurance == null) {
                item.setPatientShareAmount(net);
                item.setInsuranceShareAmount(ZERO);
                continue;
            }

            InsuranceSplit split =
                    insurancePatientShareCalculator.calculateSplit(
                            insurance,
                            serviceProduct,
                            net
                    );

            item.setPatientShareAmount(split.patientShare());
            item.setInsuranceShareAmount(split.insuranceShare());
        }

        itemRepo.saveAll(items);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
