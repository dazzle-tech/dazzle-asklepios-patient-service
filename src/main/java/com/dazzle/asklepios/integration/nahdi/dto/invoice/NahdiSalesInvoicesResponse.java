package com.dazzle.asklepios.integration.nahdi.dto.invoice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * GetSalesInvoices response. Only the fields needed for the dispensing status are read; the financial
 * fields (prices, tenders, co-payment) are out of scope and ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NahdiSalesInvoicesResponse(
        Boolean success,
        // "" or [] in the samples, so read as a node.
        JsonNode error,
        @JsonProperty("Invoices") List<Invoice> invoices
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Invoice(
            @JsonProperty("Invoice_Head") Head head,
            @JsonProperty("Invoice_Items") List<Item> items
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Head(
            @JsonProperty("Invoice_No") String invoiceNo,
            @JsonProperty("Invoice_Type") String invoiceType,
            @JsonProperty("Invoice_Date") String invoiceDate,
            // "yyyy-MM-dd HH:mm:ss", Saudi time
            @JsonProperty("Invoice_Time") String invoiceTime,
            @JsonProperty("Tran_No") String tranNo,
            // Our pres_RefID (the prescription number)
            @JsonProperty("Provider_Referance") String providerReference
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            // Our line_No: identifies the prescription line, even for a substitute product
            @JsonProperty("Item_LineNo") String lineNo,
            @JsonProperty("Item_Code") String nahdiCode,
            @JsonProperty("Item_SFDA") String sfda,
            @JsonProperty("Item_GTIN") String gtin,
            @JsonProperty("GenericCode") String genericCode,
            @JsonProperty("Item_Description") String description,
            @JsonProperty("Item_Qty") String qty,
            @JsonProperty("Item_Status") String status
    ) {}
}
