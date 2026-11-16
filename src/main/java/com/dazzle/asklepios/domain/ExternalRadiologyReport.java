package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * A radiology report brought by the patient from an outside facility, extracted by the AI document processor.
 */
@Entity
@Table(name = "external_radiology_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class ExternalRadiologyReport extends AbstractAuditingEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "encounter_id")
    private Long encounterId;

    @Column(name = "attachment_id")
    private Long attachmentId;

    @Column(name = "test_name", length = 255)
    private String testName;

    @Column(name = "report_date")
    private LocalDate reportDate;

    @Column(name = "report", columnDefinition = "text")
    private String report;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 50)
    private Severity severity;

    @Column(name = "radiologist_information", columnDefinition = "text")
    private String radiologistInformation;

    @Column(name = "critical_findings", columnDefinition = "text")
    private String criticalFindings;

    @Column(name = "radiologist_comments", columnDefinition = "text")
    private String radiologistComments;

    @Column(name = "physician_name", length = 255)
    private String physicianName;

    @Column(name = "raw_extracted_text", columnDefinition = "text")
    private String rawExtractedText;

    @Column(name = "detected_language", length = 10)
    private String detectedLanguage;

    @Builder.Default
    @Column(name = "translated", nullable = false)
    private Boolean translated = false;
}
