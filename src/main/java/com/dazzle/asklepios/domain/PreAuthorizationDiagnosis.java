package com.dazzle.asklepios.domain;

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


@Entity
@Table(name = "pre_authorization_diagnosis")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthorizationDiagnosis extends AbstractAuditingEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pre_authorization_id", nullable = false)
    private PreAuthorizationRequest preAuthorization;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "diagnosis_code", nullable = false, length = 100)
    private String diagnosisCode;

    @Column(name = "diagnosis_description", length = 500)
    private String diagnosisDescription;

    @Column(name = "diagnosis_type", length = 50)
    private String diagnosisType;

    @Column(name = "on_admission", length = 50)
    private String onAdmission;
}