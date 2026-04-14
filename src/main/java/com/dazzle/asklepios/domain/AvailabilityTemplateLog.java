package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.FinancialDetails;
import com.dazzle.asklepios.domain.enumeration.TemplateStatus;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import com.dazzle.asklepios.service.dto.workingDays.WorkingDayJson;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "availability_template_log")
@Getter
@Setter
public class AvailabilityTemplateLog extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "operation_type", nullable = false, length = 10)
    private String operationType;

    @Column(name = "log_date", nullable = false)
    private Instant logDate;

    @Column(name = "log_by", length = 50)
    @NotNull
    private String logBy;

    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 30)
    private TemplateType templateType;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "template_color", length = 20)
    private String templateColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TemplateStatus status;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "copy_from_template_id")
    private Long copyFromTemplateId;

    @Column(name = "parent_template_id")
    private Long parentTemplateId;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "default_buffer_before_minutes", nullable = false)
    private Integer defaultBufferBeforeMinutes;

    @Column(name = "default_buffer_after_minutes", nullable = false)
    private Integer defaultBufferAfterMinutes;

    @Column(name = "parallel_capacity_value", nullable = false)
    private Integer parallelCapacityValue;

    @Column(name = "default_service_id")
    private Long defaultServiceId;

    @Column(name = "number_of_resources_expected")
    private Integer numberOfResourcesExpected;

    @Column(name = "require_practitioner", nullable = false)
    private Boolean requirePractitioner;

    @Column(name = "default_practitioner_id")
    private Long defaultPractitionerId;

    @Column(name = "require_billing", nullable = false)
    private Boolean requireBilling;

    @Column(name = "require_pre_assessment", nullable = false)
    private Boolean requirePreAssessment;

    @Column(name = "allow_patient_portal_booking", nullable = false)
    private Boolean allowPatientPortalBooking;

    @Column(name = "require_confirmation", nullable = false)
    private Boolean requireConfirmation;

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_details", nullable = false, length = 50)
    private FinancialDetails financialDetails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "working_days", columnDefinition = "json", nullable = false)
    private List<WorkingDayJson> workingDays;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
