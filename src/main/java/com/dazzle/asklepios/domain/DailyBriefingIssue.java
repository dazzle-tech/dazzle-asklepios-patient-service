package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_briefing_issue")
@Getter
@Setter
public class DailyBriefingIssue implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "practitioner_id")
    private Long practitionerId;

    @Column(name = "specialty", length = 255)
    private String specialty;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "language", nullable = false, length = 8)
    private String language;

    @Column(name = "recipient", nullable = false, length = 100)
    private String recipient;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "errors", columnDefinition = "text")
    private String errors;

    @Column(name = "supersede_reason", length = 500)
    private String supersedeReason;

    @Column(name = "superseded", nullable = false)
    private boolean superseded;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;
}
