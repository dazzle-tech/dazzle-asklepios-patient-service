package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "pre_authorization_alert_close")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthorizationAlertClose extends AbstractAuditingEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "patient_service_product_id")
    private Long patientServiceProductId;

    @Column(name = "item_label", length = 500)
    private String itemLabel;

    @NotNull
    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @NotNull
    @Column(name = "closed_by", nullable = false, length = 50)
    private String closedBy;

    @NotNull
    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    @Override
    public Long getId() {
        return id;
    }
}
