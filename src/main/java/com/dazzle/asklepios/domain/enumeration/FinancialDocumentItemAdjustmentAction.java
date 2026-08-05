package com.dazzle.asklepios.domain.enumeration;

public enum FinancialDocumentItemAdjustmentAction {

    REMOVE,

    PARTIAL_CREDIT,

    /** Post-issuance discount credit on a single invoice line (patient invoices only). */
    LINE_DISCOUNT,

    REDUCE,

    ADD,

    ADD_NEW,

    INCREASE
}
