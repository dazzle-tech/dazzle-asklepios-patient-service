package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "patient_hippa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientHIPAA implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false, unique = true)
    private Long patientId;

    @Column(name = "notice_of_privacy_practice", nullable = false)
    private Boolean noticeOfPrivacyPractice;

    @Column(name = "privacy_authorization", nullable = false)
    private Boolean privacyAuthorization;

    @Column(name = "notice_of_privacy_practice_date")
    private LocalDate noticeOfPrivacyPracticeDate;

    @Column(name = "privacy_authorization_date")
    private LocalDate privacyAuthorizationDate;
}
