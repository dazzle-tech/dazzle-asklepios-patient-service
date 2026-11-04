package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.ActivityDueUnit;
import com.dazzle.asklepios.domain.enumeration.ActivityTemplateKind;
import com.dazzle.asklepios.domain.enumeration.PatientActivityPriority;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "activity_template")
@Getter
@Setter
public class ActivityTemplate extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 80, unique = true, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "category_code", nullable = false, length = 50)
    private String categoryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private ActivityTemplateKind kind = ActivityTemplateKind.TASK;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority", nullable = false, length = 20)
    private PatientActivityPriority defaultPriority = PatientActivityPriority.NORMAL;

    @Column(name = "due_offset")
    private Integer dueOffset;

    @Enumerated(EnumType.STRING)
    @Column(name = "due_unit", length = 20)
    private ActivityDueUnit dueUnit;

    /** Job roles that work this task; they see it under Team Tasks. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "assignee_roles", columnDefinition = "jsonb", nullable = false)
    private List<String> assigneeRoles = new ArrayList<>();

    /** Job roles allowed to create it by hand; empty means everyone. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "creator_roles", columnDefinition = "jsonb", nullable = false)
    private List<String> creatorRoles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outcome_options", columnDefinition = "jsonb", nullable = false)
    private List<String> outcomeOptions = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checklist", columnDefinition = "jsonb", nullable = false)
    private List<String> checklist = new ArrayList<>();

    @Column(name = "require_outcome", nullable = false)
    private Boolean requireOutcome = false;

    /** Built-in templates are recorded by the system; their code and kind stay fixed. */
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    @Column(name = "view_order", nullable = false)
    private Integer viewOrder = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
