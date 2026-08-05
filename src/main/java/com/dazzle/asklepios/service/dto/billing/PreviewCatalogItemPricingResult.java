package com.dazzle.asklepios.service.dto.billing;

import com.dazzle.asklepios.domain.enumeration.Currency;

import java.io.Serializable;
import java.math.BigDecimal;

public record PreviewCatalogItemPricingResult(

        BigDecimal setupUnitPrice,

        BigDecimal unitPrice,

        String priceSource,

        String priceListItemCode,

        Currency currency,

        BigDecimal grossAmount,

        BigDecimal discountAmount,

        BigDecimal taxAmount,

        BigDecimal netAmount,

        BigDecimal itemGrossAmount,

        BigDecimal itemDiscountAmount,

        BigDecimal itemTaxAmount,

        BigDecimal invoiceDiscountAmount,

        BigDecimal invoiceTaxAmount

) implements Serializable {
}
