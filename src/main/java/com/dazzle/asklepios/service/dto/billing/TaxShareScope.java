package com.dazzle.asklepios.service.dto.billing;

/**
 * Which share an invoice-level document carries, for the tax setup flags.
 */
public enum TaxShareScope {

    /** Cash / self-pay: tax always applies. */
    CASH,

    /** Patient share of an insurance visit: tax applies when Patient Copayment is on. */
    PATIENT_COPAYMENT,

    /** Insurance share (claim): tax applies when Claim Share is on. */
    CLAIM_SHARE
}
