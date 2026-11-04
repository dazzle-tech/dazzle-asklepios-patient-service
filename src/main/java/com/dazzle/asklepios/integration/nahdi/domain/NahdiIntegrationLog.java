package com.dazzle.asklepios.integration.nahdi.domain;

import com.dazzle.asklepios.integration.nahdi.domain.enumeration.NahdiIntegrationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

/**
 * Technical audit trail of every request/response exchanged with Nahdi.
 */
@Entity
@Table(name = "nahdi_integration_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class NahdiIntegrationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;

    @Column(name = "request_payload", columnDefinition = "text")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "text")
    private String responsePayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private NahdiIntegrationStatus status;

    /**
     * Nahdi order number returned on a successful SendPrescription.
     */
    @Column(name = "order_number", length = 100)
    private String orderNumber;

    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;

    @Override
    public String toString() {
        return "NahdiIntegrationLog{id=" + id + ", endpoint='" + endpoint + "', status=" + status + ", orderNumber='" + orderNumber + "', createdDate=" + createdDate + "}";
    }
}
