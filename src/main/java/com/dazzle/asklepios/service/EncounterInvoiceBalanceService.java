package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.service.dto.billing.EncounterInvoiceBalance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EncounterInvoiceBalanceService {

    private static final int MONEY_SCALE = 4;

    private final FinancialDocumentRepository financialDocumentRepository;
    private final FinancialDocumentBalanceService financialDocumentBalanceService;

    public EncounterInvoiceBalance resolveForEncounter(Long encounterId) {
        if (encounterId == null) {
            return EncounterInvoiceBalance.empty();
        }

        FinancialDocument invoice =
                financialDocumentRepository
                        .findFirstByEncounterIdAndDocumentTypeAndDocumentSubtypeOrderByIdDesc(
                                encounterId,
                                FinancialDocumentType.INVOICE,
                                FinancialDocumentSubtype.PATIENT
                        )
                        .orElse(null);

        if (invoice == null) {
            invoice =
                    financialDocumentRepository
                            .findFirstByEncounterIdAndDocumentTypeOrderByIdDesc(
                                    encounterId,
                                    FinancialDocumentType.INVOICE
                            )
                            .orElse(null);
        }

        if (invoice == null) {
            return EncounterInvoiceBalance.empty();
        }

        BigDecimal totalAmount = money(invoice.getTotalAmount());
        BigDecimal outstanding =
                money(
                        financialDocumentBalanceService.calculateOutstanding(
                                invoice.getId()
                        )
                );
        BigDecimal paidAmount =
                totalAmount
                        .subtract(outstanding)
                        .max(BigDecimal.ZERO)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new EncounterInvoiceBalance(
                invoice.getId(),
                invoice.getDocumentNumber(),
                totalAmount,
                paidAmount,
                outstanding
        );
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
