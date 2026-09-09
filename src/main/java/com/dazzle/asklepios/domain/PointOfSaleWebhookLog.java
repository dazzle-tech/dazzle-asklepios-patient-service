package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@Entity
@Table(name = "point_of_sale_webhook_log")
public class PointOfSaleWebhookLog
        extends AbstractAuditingEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "point_of_sale_transaction_id"
    )
    private PointOfSaleTransaction transaction;

    @Column(
            name = "external_transaction_id",
            nullable = false
    )
    private String externalTransactionId;

    @Column(
            name = "order_id"
    )
    private String orderId;

    @Column(
            name = "response_code"
    )
    private String responseCode;

    @Column(
            name = "response_message",
            length = 500
    )
    private String responseMessage;

    @Column(
            name = "transaction_status"
    )
    private String transactionStatus;

    @Column(
            name = "rrn"
    )
    private String rrn;

    @Column(
            name = "auth_code"
    )
    private String authCode;

    @Column(
            name = "terminal_id"
    )
    private String terminalId;

    @Column(
            name = "merchant_id"
    )
    private String merchantId;

    @Column(
            name = "batch_no"
    )
    private String batchNo;

    @Column(
            name = "stan_no"
    )
    private String stanNo;

    @Column(
            name = "scheme_label"
    )
    private String schemeLabel;

    @Column(
            name = "product_info",
            length = 500
    )
    private String productInfo;

    @Column(
            name = "merchant_name"
    )
    private String merchantName;

    @Column(
            name = "merchant_address",
            length = 500
    )
    private String merchantAddress;

    @Column(
            name = "application_version"
    )
    private String applicationVersion;

    @Column(
            name = "ecr_transaction_reference_number"
    )
    private String ecrTransactionReferenceNumber;

    @Column(
            name = "processed",
            nullable = false
    )
    private Boolean processed =
            Boolean.FALSE;

    @Column(
            name = "processing_status"
    )
    private String processingStatus;

    @Lob
    @Column(
            name = "processing_error"
    )
    private String processingError;

    @Lob
    @Column(
            name = "raw_payload"
    )
    private String rawPayload;
}