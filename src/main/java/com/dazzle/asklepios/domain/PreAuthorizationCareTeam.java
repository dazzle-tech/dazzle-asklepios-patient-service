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
@Table(name = "pre_authorization_care_team")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthorizationCareTeam extends AbstractAuditingEntity<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pre_authorization_id", nullable = false)
    private PreAuthorizationRequest preAuthorization;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "practitioner_name", nullable = false)
    private String practitionerName;

    @Column(name = "physician_code")
    private String physicianCode;

    @Column(name = "practitioner_role")
    private String practitionerRole;

    @Column(name = "care_team_role")
    private String careTeamRole;

    @Column(name = "speciality")
    private String speciality;

    @Column(name = "speciality_code")
    private String specialityCode;

    @Column(name = "qualification_code")
    private String qualificationCode;
}