package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.RadiologyImageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;


@Entity
@Table(name = "diagnostic_order_tests_report_image_status_log")
@Setter
@Getter
public class DiagnosticOrderTestReportImageStatusLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "status_date", nullable = false)
    private Instant statusDate;

    @Column(name = "status_by")
    private String statusBy;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_value", nullable = false)
    private RadiologyImageStatus statusValue;

}

