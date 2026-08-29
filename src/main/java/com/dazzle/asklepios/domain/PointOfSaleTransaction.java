package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.pointOfSale.PointOfSaleSourceType;
import com.dazzle.asklepios.domain.enumeration.pointOfSale.PointOfSaleTransactionStatus;
import com.dazzle.asklepios.domain.enumeration.pointOfSale.PointOfSaleTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "point_of_sale_transaction")
@Getter
@Setter
public class PointOfSaleTransaction extends AbstractAuditingEntity<Long> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private PointOfSaleSourceType sourceType;

    @NotNull
    @Column(name = "source_reference_id", nullable = false)
    private Long sourceReferenceId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_payment_id")
    private PatientPayments patientPayment;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuration_id", nullable = false)
    private PointOfSaleConfiguration configuration;

    @NotNull
    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;



    @Column(name = "external_transaction_id")
    private String externalTransactionId;
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private PointOfSaleTransactionType transactionType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false)
    private PointOfSaleTransactionStatus transactionStatus;

    @NotNull
    @Column(name = "amount", precision = 21, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency_code")
    private String currencyCode;

    @Column(name = "response_code")
    private String responseCode;

    @Column(name = "response_message")
    private String responseMessage;

    @Column(name = "rrn")
    private String rrn;

    @Column(name = "auth_code")
    private String authCode;

    @Column(name = "terminal_id")
    private String terminalId;

    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "batch_no")
    private String batchNo;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "scheme_label")
    private String schemeLabel;
    @Column(name = "stan_no")
    private String stanNo;

    @Column(name = "product_info")
    private String productInfo;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "merchant_address")
    private String merchantAddress;

    @Column(name = "ecr_transaction_reference_number")
    private String ecrTransactionReferenceNumber;

    @Column(name = "application_version")
    private String applicationVersion;
    @Column(name = "transaction_date")
    private Instant transactionDate;

    @NotNull
    @Column(name = "webhook_received", nullable = false)
    private Boolean webhookReceived = Boolean.FALSE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_transaction_id")
    private PointOfSaleTransaction originalTransaction;

    @Lob
    @Column(name = "raw_request")
    private String rawRequest;

    @Lob
    @Column(name = "raw_response")
    private String rawResponse;

}
