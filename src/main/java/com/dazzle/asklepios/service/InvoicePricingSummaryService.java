package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.SetupBillingAdjustmentClient;
import com.dazzle.asklepios.client.setup.dto.BillingAdjustmentResolveRequest;
import com.dazzle.asklepios.client.setup.dto.BillingAdjustmentResolveResponse;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.TaxApplicableOn;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.service.dto.billing.InvoicePricingSummaryResponse;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvoicePricingSummaryService {

    private static final Logger LOG =
            LoggerFactory.getLogger(InvoicePricingSummaryService.class);

    private static final String ENTITY_NAME = "invoicePricingSummary";

    private static final int MONEY_SCALE = 4;

    private final FinancialDocumentRepository financialDocumentRepository;
    private final FinancialDocumentItemRepository financialDocumentItemRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final SetupBillingAdjustmentClient setupBillingAdjustmentClient;

    public InvoicePricingSummaryResponse getByInvoiceId(Long invoiceId) {
        FinancialDocument invoice = financialDocumentRepository
                .findById(invoiceId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Invoice not found with id " + invoiceId,
                                ENTITY_NAME,
                                "invoice.notFound"
                        )
                );

        PatientEncounter encounter = patientEncounterRepository
                .findById(invoice.getEncounterId())
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Encounter not found for invoice " + invoiceId,
                                ENTITY_NAME,
                                "encounter.notFound"
                        )
                );

        List<FinancialDocumentItem> items =
                financialDocumentItemRepository
                        .findByDocument_Id(invoiceId)
                        .stream()
                        .sorted(Comparator.comparing(FinancialDocumentItem::getId))
                        .toList();

        BigDecimal grossAmount = sum(items, FinancialDocumentItem::getGrossAmount);
        BigDecimal discountAmount = sum(items, FinancialDocumentItem::getDiscountAmount);
        BigDecimal taxAmount = sum(items, FinancialDocumentItem::getTaxAmount);
        BigDecimal netAmount = money(invoice.getTotalAmount());

        if (netAmount.signum() == 0) {
            netAmount = sum(items, FinancialDocumentItem::getNetAmount);
        }

        LocalDate pricingDate =
                invoice.getCreatedDate() == null
                        ? LocalDate.now()
                        : invoice.getCreatedDate()
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate();

        List<InvoicePricingSummaryResponse.AppliedDiscountRule> discountRules =
                buildDiscountRules(
                        encounter.getFacilityId(),
                        invoice.getCurrency(),
                        pricingDate,
                        discountAmount
                );

        List<InvoicePricingSummaryResponse.AppliedTaxRule> taxRules =
                buildTaxRules(
                        encounter.getFacilityId(),
                        invoice.getCurrency(),
                        pricingDate,
                        taxAmount
                );

        return new InvoicePricingSummaryResponse(
                invoice.getId(),
                invoice.getDocumentNumber(),
                invoice.getCurrency(),
                grossAmount,
                discountAmount,
                taxAmount,
                netAmount,
                discountRules,
                taxRules
        );
    }

    private List<InvoicePricingSummaryResponse.AppliedDiscountRule> buildDiscountRules(
            Long facilityId,
            com.dazzle.asklepios.domain.enumeration.Currency currency,
            LocalDate pricingDate,
            BigDecimal totalDiscountAmount
    ) {
        List<InvoicePricingSummaryResponse.AppliedDiscountRule> rules =
                new ArrayList<>();

        appendDiscountRule(
                rules,
                resolveRules(
                        facilityId,
                        currency,
                        pricingDate,
                        null,
                        DiscountApplicableOn.INVOICE_LINE
                ),
                totalDiscountAmount,
                true
        );
        appendDiscountRule(
                rules,
                resolveRules(
                        facilityId,
                        currency,
                        pricingDate,
                        null,
                        DiscountApplicableOn.INVOICE
                ),
                totalDiscountAmount,
                rules.isEmpty()
        );

        if (rules.isEmpty() && totalDiscountAmount.signum() > 0) {
            rules.add(
                    new InvoicePricingSummaryResponse.AppliedDiscountRule(
                            null,
                            null,
                            "Applied discount",
                            null,
                            null,
                            null,
                            null,
                            totalDiscountAmount
                    )
            );
        }

        return rules;
    }

    private List<InvoicePricingSummaryResponse.AppliedTaxRule> buildTaxRules(
            Long facilityId,
            com.dazzle.asklepios.domain.enumeration.Currency currency,
            LocalDate pricingDate,
            BigDecimal totalTaxAmount
    ) {
        List<InvoicePricingSummaryResponse.AppliedTaxRule> rules =
                new ArrayList<>();

        appendTaxRule(
                rules,
                resolveRules(
                        facilityId,
                        currency,
                        pricingDate,
                        TaxApplicableOn.INVOICE_LINE,
                        null
                ),
                totalTaxAmount,
                true
        );
        appendTaxRule(
                rules,
                resolveRules(
                        facilityId,
                        currency,
                        pricingDate,
                        TaxApplicableOn.INVOICE,
                        null
                ),
                totalTaxAmount,
                rules.isEmpty()
        );

        if (rules.isEmpty() && totalTaxAmount.signum() > 0) {
            rules.add(
                    new InvoicePricingSummaryResponse.AppliedTaxRule(
                            null,
                            null,
                            "Applied tax",
                            null,
                            null,
                            null,
                            null,
                            null,
                            totalTaxAmount
                    )
            );
        }

        return rules;
    }

    private void appendDiscountRule(
            List<InvoicePricingSummaryResponse.AppliedDiscountRule> rules,
            BillingAdjustmentResolveResponse response,
            BigDecimal totalDiscountAmount,
            boolean assignFullAmount
    ) {
        if (response == null || response.discountId() == null) {
            return;
        }

        rules.add(
                new InvoicePricingSummaryResponse.AppliedDiscountRule(
                        response.discountId(),
                        response.discountCode(),
                        response.discountName(),
                        response.discountApplicableOn(),
                        response.discountType(),
                        response.discountRate(),
                        response.discountFixedAmount(),
                        assignFullAmount ? totalDiscountAmount : BigDecimal.ZERO
                )
        );
    }

    private void appendTaxRule(
            List<InvoicePricingSummaryResponse.AppliedTaxRule> rules,
            BillingAdjustmentResolveResponse response,
            BigDecimal totalTaxAmount,
            boolean assignFullAmount
    ) {
        if (response == null || response.taxId() == null) {
            return;
        }

        rules.add(
                new InvoicePricingSummaryResponse.AppliedTaxRule(
                        response.taxId(),
                        response.taxCode(),
                        response.taxName(),
                        response.taxApplicableOn(),
                        response.taxType(),
                        response.taxCalculationType(),
                        response.taxRate(),
                        response.taxFixedAmount(),
                        assignFullAmount ? totalTaxAmount : BigDecimal.ZERO
                )
        );
    }

    private BillingAdjustmentResolveResponse resolveRules(
            Long facilityId,
            com.dazzle.asklepios.domain.enumeration.Currency currency,
            LocalDate pricingDate,
            TaxApplicableOn taxApplicableOn,
            DiscountApplicableOn discountApplicableOn
    ) {
        if (taxApplicableOn == null && discountApplicableOn == null) {
            return null;
        }

        try {
            return setupBillingAdjustmentClient.resolveAdjustments(
                    new BillingAdjustmentResolveRequest(
                            facilityId,
                            currency,
                            taxApplicableOn,
                            discountApplicableOn,
                            pricingDate
                    )
            );
        } catch (FeignException exception) {
            LOG.warn(
                    "Unable to resolve invoice pricing rules facilityId={}: {}",
                    facilityId,
                    exception.getMessage()
            );
            return null;
        }
    }

    private BigDecimal sum(
            List<FinancialDocumentItem> items,
            java.util.function.Function<FinancialDocumentItem, BigDecimal> mapper
    ) {
        return items.stream()
                .map(mapper)
                .map(this::money)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
