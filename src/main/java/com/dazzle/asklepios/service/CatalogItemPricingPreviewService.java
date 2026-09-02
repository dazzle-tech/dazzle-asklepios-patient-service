package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountType;
import com.dazzle.asklepios.domain.enumeration.billing.TaxApplicableOn;
import com.dazzle.asklepios.domain.enumeration.billing.TaxType;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import com.dazzle.asklepios.service.dto.billing.BillingPricingInput;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.InvoiceItemPricingAdjustmentSnapshot;
import com.dazzle.asklepios.service.dto.billing.PreviewCatalogItemPricingRequest;
import com.dazzle.asklepios.service.dto.billing.PreviewCatalogItemPricingResult;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import com.dazzle.asklepios.service.dto.billing.ResolvedBillingPrice;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatalogItemPricingPreviewService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    CatalogItemPricingPreviewService.class
            );

    private static final String ENTITY_NAME =
            "catalogItemPricingPreview";

    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final BillingEngineService billingEngineService;
    private final BillingPricingInputFactory billingPricingInputFactory;
    private final BillingPricingService billingPricingService;
    private final InsurancePatientShareCalculator insurancePatientShareCalculator;
    private final InvoiceApplicableOnAdjustmentService invoiceApplicableOnAdjustmentService;
    private final FinancialDocumentRepository financialDocumentRepository;
    private final FinancialDocumentItemRepository financialDocumentItemRepository;
    private final InvoiceItemPricingSnapshotService invoiceItemPricingSnapshotService;

    private static final int MONEY_SCALE = 4;

    @Transactional(readOnly = true)
    public PreviewCatalogItemPricingResult preview(
            PreviewCatalogItemPricingRequest request
    ) {
        validateRequest(request);

        Patient patient = loadPatient(request.patientId());
        PatientEncounter encounter = loadAndValidateEncounter(
                request,
                patient
        );
        PatientInsurance insurance = resolveInsurance(request, encounter);

        boolean insuranceVisit = insurance != null;
        boolean eligibilityInForce =
                insuranceVisit
                        && insurancePatientShareCalculator.isLatestCoverageInForce(
                                insurance
                        );
        BillingCoverageType coverageType =
                insuranceVisit && eligibilityInForce
                        ? BillingCoverageType.INSURANCE
                        : BillingCoverageType.SELF_PAY;

        PatientServiceAndProduct previewItem =
                buildPreviewItem(
                        request,
                        encounter,
                        insurance,
                        coverageType
                );

        ResolvedBillingPrice resolvedPrice =
                billingEngineService.resolvePricing(
                        previewItem,
                        request.facilityId(),
                        coverageType
                );

        boolean coveredByInsurance =
                insuranceVisit
                        && eligibilityInForce
                        && resolvedPrice.resolvedFromPriceList();
        boolean requiresCashConfirmation = insuranceVisit && !coveredByInsurance;
        String notCoveredReason = null;

        if (requiresCashConfirmation) {
            notCoveredReason = eligibilityInForce
                    ? InsurancePriceListCoverageService.NOT_IN_INSURANCE_PRICE_LIST
                    : InsurancePriceListCoverageService.ELIGIBILITY_NOT_IN_FORCE;

            if (coverageType != BillingCoverageType.SELF_PAY) {
                resolvedPrice = billingEngineService.resolvePricing(
                        previewItem,
                        request.facilityId(),
                        BillingCoverageType.SELF_PAY
                );
            }
        }

        BillingPricingInput pricingInput =
                billingPricingInputFactory.create(
                        previewItem,
                        resolvedPrice
                );
        BillingProcessingContext pricingContext =
                BillingProcessingContext.builder()
                        .patientServiceProduct(previewItem)
                        .pricingInput(pricingInput)
                        .build();
        billingPricingService.calculate(pricingContext);
        PriceCalculationResult pricing = pricingContext.getPricingResult();

        long quantity = defaultQuantity(request.quantity());
        BigDecimal unitPrice = money(pricing.unitPrice());
        BigDecimal itemGross = money(pricing.grossAmount());
        BigDecimal itemDiscount = money(pricing.discountAmount());
        BigDecimal itemTax = money(pricing.taxAmount());

        FinancialDocumentItem draftItem =
                buildDraftPreviewItem(
                        previewItem,
                        quantity,
                        unitPrice,
                        itemGross
                );
        draftItem.setDiscountAmount(itemDiscount);
        draftItem.setTaxAmount(itemTax);
        draftItem.setNetAmount(money(pricing.netAmount()));
        draftItem.setPatientShareAmount(draftItem.getNetAmount());
        draftItem.setRemainingAmount(draftItem.getNetAmount());

        applyInvoiceScopeAdjustments(
                draftItem,
                request,
                itemDiscount,
                itemTax
        );

        BigDecimal totalDiscount = money(draftItem.getDiscountAmount());
        BigDecimal totalTax = money(draftItem.getTaxAmount());
        BigDecimal finalNet = money(draftItem.getNetAmount());
        BigDecimal invoiceDiscount =
                totalDiscount
                        .subtract(itemDiscount)
                        .max(BigDecimal.ZERO)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal invoiceTax =
                totalTax
                        .subtract(itemTax)
                        .max(BigDecimal.ZERO)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        String priceListItemCode =
                resolvedPrice.pricingResponse() == null
                        ? null
                        : resolvedPrice
                        .pricingResponse()
                        .priceListItemCode();

        BigDecimal patientShareAmount = finalNet;
        BigDecimal insuranceShareAmount = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (insurance != null
                && !requiresCashConfirmation
                && !Boolean.TRUE.equals(previewItem.getIsExempted())) {
            InsuranceSplit split =
                    insurancePatientShareCalculator.calculateSplit(
                            insurance,
                            previewItem,
                            finalNet
                    );
            patientShareAmount = money(split.patientShare());
            insuranceShareAmount = money(split.insuranceShare());
        }

        LOG.info(
                "[PREVIEW_CATALOG_PRICING] encounterId={} invoiceId={} itemType={} "
                        + "source={} gross={} itemDisc={} net={} patientShare={} insuranceShare={}",
                request.encounterId(),
                request.invoiceId(),
                request.billingItemType(),
                resolvedPrice.priceSource(),
                itemGross,
                itemDiscount,
                finalNet,
                patientShareAmount,
                insuranceShareAmount
        );

        return new PreviewCatalogItemPricingResult(
                resolvedPrice.setupUnitPrice(),
                resolvedPrice.unitPrice(),
                resolvedPrice.priceSource() == null
                        ? BillingPriceSource.SETUP_FALLBACK.name()
                        : resolvedPrice.priceSource().name(),
                priceListItemCode,
                resolvedPrice.currency() == null
                        ? request.currency()
                        : resolvedPrice.currency(),
                itemGross,
                totalDiscount,
                totalTax,
                finalNet,
                itemGross,
                itemDiscount,
                itemTax,
                invoiceDiscount,
                invoiceTax,
                patientShareAmount,
                insuranceShareAmount,
                insuranceVisit,
                coveredByInsurance,
                requiresCashConfirmation,
                notCoveredReason,
                requiresCashConfirmation ? resolvedPrice.unitPrice() : null
        );
    }

    /**
     * Applies invoice-wide tax/discount to debit-note draft lines, inferring
     * rates from the parent invoice when active setup rules are unavailable.
     */
    public void applyDebitNoteInvoiceAdjustments(
            List<FinancialDocumentItem> items,
            Long invoiceId,
            Long facilityId,
            Currency currency,
            LocalDate pricingDate
    ) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<BigDecimal> baselineDiscounts = new ArrayList<>();
        List<BigDecimal> baselineTaxes = new ArrayList<>();
        for (FinancialDocumentItem item : items) {
            baselineDiscounts.add(money(item.getDiscountAmount()));
            baselineTaxes.add(money(item.getTaxAmount()));
        }

        invoiceApplicableOnAdjustmentService.applyInvoiceScopeAdjustmentsOnly(
                items,
                facilityId,
                currency,
                pricingDate
        );

        if (invoiceId == null) {
            return;
        }

        for (int index = 0; index < items.size(); index++) {
            FinancialDocumentItem item = items.get(index);
            BigDecimal itemDiscount = baselineDiscounts.get(index);
            BigDecimal itemTax = baselineTaxes.get(index);
            BigDecimal invoiceDiscount =
                    money(item.getDiscountAmount()).subtract(itemDiscount);
            BigDecimal invoiceTax =
                    money(item.getTaxAmount()).subtract(itemTax);

            if (invoiceDiscount.signum() <= 0 && invoiceTax.signum() <= 0) {
                applyInferredInvoiceAdjustmentsFromInvoice(
                        item,
                        invoiceId,
                        itemDiscount,
                        itemTax
                );
            }
        }
    }

    private void applyInvoiceScopeAdjustments(
            FinancialDocumentItem draftItem,
            PreviewCatalogItemPricingRequest request,
            BigDecimal itemDiscount,
            BigDecimal itemTax
    ) {
        List<FinancialDocumentItem> adjustmentItems =
                new ArrayList<>(List.of(draftItem));

        invoiceApplicableOnAdjustmentService.applyInvoiceScopeAdjustmentsOnly(
                adjustmentItems,
                request.facilityId(),
                request.currency(),
                resolvePricingDate(request.invoiceId())
        );

        BigDecimal invoiceDiscount =
                money(draftItem.getDiscountAmount()).subtract(itemDiscount);
        BigDecimal invoiceTax =
                money(draftItem.getTaxAmount()).subtract(itemTax);

        if (request.invoiceId() != null
                && invoiceDiscount.signum() <= 0
                && invoiceTax.signum() <= 0) {
            applyInferredInvoiceAdjustmentsFromInvoice(
                    draftItem,
                    request.invoiceId(),
                    itemDiscount,
                    itemTax
            );
        }
    }

    private void applyInferredInvoiceAdjustmentsFromInvoice(
            FinancialDocumentItem draftItem,
            Long invoiceId,
            BigDecimal itemDiscount,
            BigDecimal itemTax
    ) {
        List<FinancialDocumentItem> invoiceItems =
                financialDocumentItemRepository.findByDocument_Id(invoiceId);

        if (invoiceItems.isEmpty()) {
            return;
        }

        Optional<InvoiceSampleRates> sampleRates =
                invoiceItems.stream()
                        .filter(item -> money(item.getGrossAmount()).signum() > 0)
                        .map(this::resolveInvoiceSampleRates)
                        .filter(
                                rates ->
                                        rates.invoiceDiscount().signum() > 0
                                                || rates.invoiceTax().signum() > 0
                        )
                        .max(
                                Comparator.comparing(
                                        rates ->
                                                rates.invoiceDiscount().add(
                                                        rates.invoiceTax()
                                                )
                                )
                        );

        if (sampleRates.isEmpty()) {
            LOG.info(
                    "[PREVIEW_CATALOG_PRICING] no invoice adjustment sample invoiceId={}",
                    invoiceId
            );
            return;
        }

        InvoiceSampleRates rates = sampleRates.get();
        BigDecimal gross = money(draftItem.getGrossAmount());

        BigDecimal inferredInvoiceDiscount =
                gross
                        .multiply(rates.discountRate())
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal afterDiscount =
                gross
                        .subtract(inferredInvoiceDiscount)
                        .max(BigDecimal.ZERO)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal inferredInvoiceTax =
                afterDiscount
                        .multiply(rates.taxRate())
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal finalNet =
                afterDiscount
                        .add(inferredInvoiceTax)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        draftItem.setDiscountAmount(
                itemDiscount.add(inferredInvoiceDiscount)
        );
        draftItem.setTaxAmount(itemTax.add(inferredInvoiceTax));
        draftItem.setNetAmount(finalNet);
        draftItem.setPatientShareAmount(finalNet);
        draftItem.setRemainingAmount(finalNet);

        LOG.info(
                "[PREVIEW_CATALOG_PRICING] inferred invoice adjustments invoiceId={} "
                        + "sampleGross={} discountRate={} taxRate={} net={}",
                invoiceId,
                rates.sampleGross(),
                rates.discountRate(),
                rates.taxRate(),
                finalNet
        );
    }

    private InvoiceSampleRates resolveInvoiceSampleRates(
            FinancialDocumentItem sample
    ) {
        InvoiceItemPricingAdjustmentSnapshot snapshot =
                invoiceItemPricingSnapshotService.readSnapshot(sample);

        BigDecimal sampleGross = money(sample.getGrossAmount());
        BigDecimal sampleInvoiceDiscount =
                sumInvoiceScopeDiscounts(snapshot);
        BigDecimal sampleInvoiceTax =
                sumInvoiceScopeTaxes(snapshot);

        if (sampleInvoiceDiscount.signum() <= 0) {
            BigDecimal itemLevelDiscount =
                    sumNonInvoiceScopeDiscounts(snapshot);
            sampleInvoiceDiscount =
                    money(sample.getDiscountAmount())
                            .subtract(itemLevelDiscount)
                            .max(BigDecimal.ZERO);
        }

        if (sampleInvoiceTax.signum() <= 0) {
            BigDecimal itemLevelTax =
                    sumNonInvoiceScopeTaxes(snapshot);
            sampleInvoiceTax =
                    money(sample.getTaxAmount())
                            .subtract(itemLevelTax)
                            .max(BigDecimal.ZERO);
        }

        BigDecimal discountRate =
                resolveDiscountRate(
                        snapshot,
                        sampleGross,
                        sampleInvoiceDiscount
                );
        BigDecimal sampleAfterDiscount =
                sampleGross
                        .subtract(sampleInvoiceDiscount)
                        .max(BigDecimal.ZERO)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal taxRate =
                resolveTaxRate(
                        snapshot,
                        sampleAfterDiscount,
                        sampleInvoiceTax
                );

        return new InvoiceSampleRates(
                sampleGross,
                sampleInvoiceDiscount,
                sampleInvoiceTax,
                discountRate,
                taxRate
        );
    }

    private BigDecimal resolveDiscountRate(
            InvoiceItemPricingAdjustmentSnapshot snapshot,
            BigDecimal sampleGross,
            BigDecimal sampleInvoiceDiscount
    ) {
        if (snapshot.discounts() != null) {
            Optional<BigDecimal> configuredRate =
                    snapshot.discounts().stream()
                            .filter(
                                    entry ->
                                            entry.applicableOn()
                                                    == DiscountApplicableOn.INVOICE
                            )
                            .filter(
                                    entry ->
                                            entry.discountType()
                                                    == DiscountType.PERCENTAGE
                            )
                            .map(entry -> money(entry.rate()))
                            .filter(rate -> rate.signum() > 0)
                            .findFirst();

            if (configuredRate.isPresent()) {
                return configuredRate
                        .get()
                        .divide(
                                BigDecimal.valueOf(100),
                                MONEY_SCALE,
                                RoundingMode.HALF_UP
                        );
            }
        }

        if (sampleGross.signum() <= 0 || sampleInvoiceDiscount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return sampleInvoiceDiscount.divide(
                sampleGross,
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal resolveTaxRate(
            InvoiceItemPricingAdjustmentSnapshot snapshot,
            BigDecimal sampleAfterDiscount,
            BigDecimal sampleInvoiceTax
    ) {
        if (snapshot.taxes() != null) {
            Optional<BigDecimal> configuredRate =
                    snapshot.taxes().stream()
                            .filter(
                                    entry ->
                                            entry.applicableOn()
                                                    == TaxApplicableOn.INVOICE
                            )
                            .filter(
                                    entry ->
                                            entry.taxType() == TaxType.PERCENTAGE
                            )
                            .map(entry -> money(entry.rate()))
                            .filter(rate -> rate.signum() > 0)
                            .findFirst();

            if (configuredRate.isPresent()) {
                return configuredRate
                        .get()
                        .divide(
                                BigDecimal.valueOf(100),
                                MONEY_SCALE,
                                RoundingMode.HALF_UP
                        );
            }
        }

        if (sampleAfterDiscount.signum() <= 0 || sampleInvoiceTax.signum() <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return sampleInvoiceTax.divide(
                sampleAfterDiscount,
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal sumInvoiceScopeDiscounts(
            InvoiceItemPricingAdjustmentSnapshot snapshot
    ) {
        if (snapshot.discounts() == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return snapshot.discounts().stream()
                .filter(
                        entry ->
                                entry.applicableOn()
                                        == DiscountApplicableOn.INVOICE
                )
                .map(entry -> money(entry.appliedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sumInvoiceScopeTaxes(
            InvoiceItemPricingAdjustmentSnapshot snapshot
    ) {
        if (snapshot.taxes() == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return snapshot.taxes().stream()
                .filter(
                        entry ->
                                entry.applicableOn() == TaxApplicableOn.INVOICE
                )
                .map(entry -> money(entry.appliedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sumNonInvoiceScopeDiscounts(
            InvoiceItemPricingAdjustmentSnapshot snapshot
    ) {
        if (snapshot.discounts() == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return snapshot.discounts().stream()
                .filter(
                        entry ->
                                entry.applicableOn()
                                        != DiscountApplicableOn.INVOICE
                )
                .map(entry -> money(entry.appliedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sumNonInvoiceScopeTaxes(
            InvoiceItemPricingAdjustmentSnapshot snapshot
    ) {
        if (snapshot.taxes() == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return snapshot.taxes().stream()
                .filter(
                        entry ->
                                entry.applicableOn() != TaxApplicableOn.INVOICE
                )
                .map(entry -> money(entry.appliedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private record InvoiceSampleRates(
            BigDecimal sampleGross,
            BigDecimal invoiceDiscount,
            BigDecimal invoiceTax,
            BigDecimal discountRate,
            BigDecimal taxRate
    ) {
    }

    private FinancialDocumentItem buildDraftPreviewItem(
            PatientServiceAndProduct previewItem,
            long quantity,
            BigDecimal unitPrice,
            BigDecimal grossAmount
    ) {
        return FinancialDocumentItem.builder()
                .patientServiceProductId(0L)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .grossAmount(grossAmount)
                .discountAmount(
                        BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                )
                .taxAmount(
                        BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                )
                .netAmount(grossAmount)
                .patientShareAmount(grossAmount)
                .remainingAmount(grossAmount)
                .currency(previewItem.getCurrency())
                .build();
    }

    private LocalDate resolvePricingDate(Long invoiceId) {
        if (invoiceId == null) {
            return LocalDate.now();
        }

        return financialDocumentRepository
                .findById(invoiceId)
                .map(FinancialDocument::getCreatedDate)
                .map(instant -> instant.atZone(ZoneOffset.UTC).toLocalDate())
                .orElse(LocalDate.now());
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private PatientServiceAndProduct buildPreviewItem(
            PreviewCatalogItemPricingRequest request,
            PatientEncounter encounter,
            PatientInsurance insurance,
            BillingCoverageType coverageType
    ) {
        Long sourceId = resolveSourceId(request);

        return PatientServiceAndProduct.builder()
                .patientId(request.patientId())
                .encounterId(encounter.getId())
                .billingItemType(request.billingItemType())
                .brandMedicationId(request.brandMedicationId())
                .diagnosticTestId(request.diagnosticTestId())
                .serviceId(request.serviceId())
                .procedureId(request.procedureId())
                .sourceId(sourceId)
                .serviceSource(ServiceSource.SERVICE_AND_PRODUCT)
                .quantity(
                        defaultQuantity(
                                request.quantity()
                        )
                )
                .unitPrice(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .exemptionAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .grossAmount(BigDecimal.ZERO)
                .netAmount(BigDecimal.ZERO)
                .patientShareAmount(BigDecimal.ZERO)
                .insuranceShareAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .remainingAmount(BigDecimal.ZERO)
                .currency(request.currency())
                .isBilled(Boolean.FALSE)
                .paymentStatus(PaymentStatus.PENDING)
                .coverageStatus(
                        insurance == null
                                ? CoverageStatus.NOT_CHECKED
                                : CoverageStatus.COVERED
                )
                .patientInsuranceId(
                        insurance == null
                                ? null
                                : insurance.getId()
                )
                .isDefaultService(Boolean.FALSE)
                .isExempted(Boolean.FALSE)
                .preAuthorizationRequired(Boolean.FALSE)
                .notes("Catalog pricing preview")
                .build();
    }

    private void validateRequest(
            PreviewCatalogItemPricingRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Preview request is required.",
                    ENTITY_NAME,
                    "request.required"
            );
        }

        validateItemReference(request);
    }

    private void validateItemReference(
            PreviewCatalogItemPricingRequest request
    ) {
        BillingItemTypes type = request.billingItemType();

        switch (type) {
            case MEDICATION -> {
                if (request.brandMedicationId() == null) {
                    throw new BadRequestAlertException(
                            "Medication is required.",
                            ENTITY_NAME,
                            "medication.required"
                    );
                }
            }
            case LABORATORY, RADIOLOGY, PATHOLOGY -> {
                if (request.diagnosticTestId() == null) {
                    throw new BadRequestAlertException(
                            "Diagnostic test is required.",
                            ENTITY_NAME,
                            "diagnosticTest.required"
                    );
                }
            }
            case SERVICE -> {
                if (request.serviceId() == null) {
                    throw new BadRequestAlertException(
                            "Service is required.",
                            ENTITY_NAME,
                            "service.required"
                    );
                }
            }
            case PROCEDURE -> {
                if (request.procedureId() == null) {
                    throw new BadRequestAlertException(
                            "Procedure is required.",
                            ENTITY_NAME,
                            "procedure.required"
                    );
                }
            }
            default -> throw new BadRequestAlertException(
                    "Unsupported catalog item type.",
                    ENTITY_NAME,
                    "billingItemType.unsupported"
            );
        }
    }

    private Long resolveSourceId(
            PreviewCatalogItemPricingRequest request
    ) {
        return switch (request.billingItemType()) {
            case SERVICE -> request.serviceId();
            case PROCEDURE -> request.procedureId();
            case MEDICATION -> request.brandMedicationId();
            case LABORATORY, RADIOLOGY, PATHOLOGY ->
                    request.diagnosticTestId();
        };
    }

    private Long defaultQuantity(
            BigDecimal quantity
    ) {
        if (quantity == null || quantity.signum() <= 0) {
            return 1L;
        }

        return quantity.longValue();
    }

    private Patient loadPatient(Long patientId) {
        return patientRepository
                .findById(patientId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Patient not found with id " + patientId,
                                ENTITY_NAME,
                                "patient.notfound"
                        )
                );
    }

    private PatientEncounter loadAndValidateEncounter(
            PreviewCatalogItemPricingRequest request,
            Patient patient
    ) {
        PatientEncounter encounter =
                patientEncounterRepository
                        .findById(request.encounterId())
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Encounter not found with id "
                                                + request.encounterId(),
                                        ENTITY_NAME,
                                        "encounter.notfound"
                                )
                        );

        if (encounter.getPatient() == null
                || !patient.getId().equals(encounter.getPatient().getId())) {
            throw new BadRequestAlertException(
                    "Encounter does not belong to the selected patient.",
                    ENTITY_NAME,
                    "encounter.patient.mismatch"
            );
        }

        if (!request.facilityId().equals(encounter.getFacilityId())) {
            throw new BadRequestAlertException(
                    "Encounter facility does not match the request facility.",
                    ENTITY_NAME,
                    "encounter.facility.mismatch"
            );
        }

        return encounter;
    }

    private PatientInsurance resolveInsurance(
            PreviewCatalogItemPricingRequest request,
            PatientEncounter encounter
    ) {
        Long insuranceId =
                request.patientInsuranceId() != null
                        ? request.patientInsuranceId()
                        : encounter.getPatientInsuranceId();

        if (insuranceId == null) {
            return null;
        }

        return patientInsuranceRepository
                .findById(insuranceId)
                .filter(insurance ->
                        insurance.getPatient() != null
                                && request.patientId().equals(
                                        insurance.getPatient().getId()
                                )
                )
                .orElse(null);
    }
}
