package com.dazzle.asklepios.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;


import java.io.Serializable;
import java.math.BigDecimal;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "pre_authorization_item")
public class PreAuthorizationItem extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pre_authorization_id", nullable = false)
    private Long preAuthorizationId;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "item_type", nullable = false, length = 50)
    private String itemType;

    @Column(name = "item_code", nullable = false, length = 100)
    private String itemCode;

    @Column(name = "item_description", length = 500)
    private String itemDescription;

    @Column(name = "non_standard_code", length = 100)
    private String nonStandardCode;

    @Column(name = "non_standard_desc", length = 500)
    private String nonStandardDesc;

    @Column(name = "is_package", nullable = false)
    private Boolean isPackage = false;

    @Column(name = "is_maternity", nullable = false)
    private Boolean isMaternity = false;

    @Column(name = "quantity", precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "quantity_code", length = 50)
    private String quantityCode;

    @Column(name = "unit_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "discount", precision = 19, scale = 2, nullable = false)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "factor", precision = 19, scale = 4, nullable = false)
    private BigDecimal factor = BigDecimal.ONE;

    @Column(name = "tax_percent", precision = 19, scale = 4)
    private BigDecimal taxPercent = BigDecimal.ZERO;

    @Column(name = "tax", precision = 19, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "patient_share_percent", precision = 19, scale = 4)
    private BigDecimal patientSharePercent = BigDecimal.ZERO;

    @Column(name = "patient_share", precision = 19, scale = 2)
    private BigDecimal patientShare = BigDecimal.ZERO;

    @Column(name = "payer_share", precision = 19, scale = 2)
    private BigDecimal payerShare = BigDecimal.ZERO;

    @Column(name = "net", precision = 19, scale = 2, nullable = false)
    private BigDecimal net = BigDecimal.ZERO;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "invoice_no", length = 100)
    private String invoiceNo;

    @Column(name = "body_site", length = 100)
    private String bodySite;

    @Column(name = "sub_site", length = 100)
    private String subSite;

    @Column(name = "brand_medication_id")
    private Long brandMedicationId;

    @Column(name = "diagnostic_test_id")
    private Long diagnosticTestId;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "procedure_id")
    private Long procedureId;

    @Column(name = "waseel_item_id")
    private Long waseelItemId;

    @Column(name = "item_decision", length = 100)
    private String itemDecision;

    @Column(name = "reason_codes", length = 500)
    private String reasonCodes;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;
}