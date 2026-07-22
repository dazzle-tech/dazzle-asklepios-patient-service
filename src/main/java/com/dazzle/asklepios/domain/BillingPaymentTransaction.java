package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.BillingPaymentTransactionStatus;
import com.dazzle.asklepios.domain.enumeration.BillingPaymentTransactionType;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table( name = "billing_payment_transaction")
public class BillingPaymentTransaction extends AbstractAuditingEntity<Long>
        implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @NotNull
    @Size(max = 50)
    @Column(name = "transaction_number", nullable = false, unique = true, length = 50)
    private String transactionNumber;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private BillingPayment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_transaction_id")
    private BillingPaymentTransaction parentTransaction;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private BillingPaymentTransactionType transactionType;

    @NotNull
    @Column(name = "payment_method_id", nullable = false)
    private Long paymentMethodId;

    @NotNull
    @Size(max = 50)
    @Column(name = "payment_method_code", nullable = false, length = 50)
    private String paymentMethodCode;

    @NotNull
    @DecimalMin(value = "0.0001")
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 30)
    private Currency currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillingPaymentTransactionStatus status =
            BillingPaymentTransactionStatus.PENDING;

    @NotNull
    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;

    @Size(max = 150)
    @Column(name = "external_reference", length = 150)
    private String externalReference;

    @Size(max = 100)
    @Column(name = "authorization_code", length = 100)
    private String authorizationCode;

    @Size(max = 150)
    @Column(name = "processor_reference", length = 150)
    private String processorReference;

    @Pattern(regexp = "^[0-9]{4}$")
    @Size(max = 4)
    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Size(max = 150)
    @Column(name = "bank_reference", length = 150)
    private String bankReference;

    @Column(name = "cash_register_id")
    private Long cashRegisterId;

    @Size(max = 100)
    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Size(max = 500)
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "processor_response", columnDefinition = "jsonb")
    private JsonNode processorResponse;

    @NotNull
    @Size(max = 150)
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;
}