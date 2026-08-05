package com.dazzle.asklepios.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.annotations.NotNull;

import java.time.LocalDate;

@Entity
@Table(name = "pre_authorization_supporting_info")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthorizationSupportingInfo extends AbstractAuditingEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pre_authorization_id", nullable = false)
    private PreAuthorizationRequest preAuthorization;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "code")
    private String code;

    private LocalDate fromDate;

    private LocalDate toDate;

    @Column(columnDefinition = "TEXT")
    private String value;

    private String reason;

    private String unit;

    @Column(columnDefinition = "TEXT")
    private String attachment;

    private String attachmentName;

    private String attachmentType;

    private LocalDate attachmentDate;
}