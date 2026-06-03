package com.dazzle.asklepios.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
import software.amazon.awssdk.annotations.NotNull;

@Entity
@Table(name = "pre_authorization_track")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthorizationTrack extends AbstractAuditingEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pre_authorization_id", nullable = false)
    private PreAuthorizationRequest preAuthorization;

    @Column(name = "track_type", nullable = false)
    private String trackType;

    private String status;

    private String outcome;

    @Column(length = 1000)
    private String message;

    @Column(length = 1000)
    private String disposition;

    private Long transactionId;

    private String outgoingTransactionId;

    private Long approvalRequestId;

    private Long approvalResponseId;

    @Column(columnDefinition = "TEXT")
    private String requestJson;

    @Column(columnDefinition = "TEXT")
    private String responseJson;
}