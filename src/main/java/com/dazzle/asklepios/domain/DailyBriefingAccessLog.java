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
@Table(name = "daily_briefing_access_log")
@Getter
@Setter
public class DailyBriefingAccessLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;

    @Column(name = "accessed_by", nullable = false, length = 100)
    private String accessedBy;

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

    @Column(name = "indicator", nullable = false, length = 80)
    private String indicator;

    @Column(name = "case_count", nullable = false)
    private int caseCount;

    @Column(name = "case_ids", columnDefinition = "text")
    private String caseIds;
}
