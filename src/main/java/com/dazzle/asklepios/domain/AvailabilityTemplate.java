package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.FinancialDetails;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Entity
@Table(name = "availability_template")
@Getter
@Setter
public class AvailabilityTemplate extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "facility_id", nullable = false)
    private Long facility;

    @Column(name = "department_id", nullable = false)
    private Long department;

    @Column(name = "template_name", nullable = false)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false)
    private TemplateType templateType;

    @Column(name = "template_color")
    private String templateColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TemplateStatus status;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @ManyToOne
    @JoinColumn(name = "copy_from_template_id")
    private AvailabilityTemplate copyFromTemplate;

    @ManyToOne
    @JoinColumn(name = "parent_template_id")
    private AvailabilityTemplate parentTemplate;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "default_buffer_before_minutes", nullable = false)
    private Integer defaultBufferBeforeMinutes = 0;

    @Column(name = "default_buffer_after_minutes", nullable = false)
    private Integer defaultBufferAfterMinutes = 0;

    @Column(name = "parallel_capacity_value", nullable = false)
    private Integer parallelCapacityValue = 0;

    @Column(name = "default_service_id")
    private Long defaultService;

    @Column(name = "number_of_resources_expected")
    private Integer numberOfResourcesExpected;

    @Column(name = "require_practitioner", nullable = false)
    private Boolean requirePractitioner = false;

    @Column(name = "default_practitioner_id")
    private Long defaultPractitioner;

    @Column(name = "require_billing", nullable = false)
    private Boolean requireBilling = false;

    @Column(name = "require_pre_assessment", nullable = false)
    private Boolean requirePreAssessment = false;

    @Column(name = "allow_patient_portal_booking", nullable = false)
    private Boolean allowPatientPortalBooking = false;

    @Column(name = "require_confirmation", nullable = false)
    private Boolean requireConfirmation = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_details", nullable = false)
    private FinancialDetails financialDetails;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL)
    private List<AvailabilityTemplateWorkingDay> workingDays;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL)
    private List<AvailabilityTemplateInterval> intervals;
}
