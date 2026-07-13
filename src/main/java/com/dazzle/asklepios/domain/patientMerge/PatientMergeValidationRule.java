package com.dazzle.asklepios.domain.patientMerge;

import com.dazzle.asklepios.domain.enumeration.AppliesTo;
import com.dazzle.asklepios.domain.enumeration.MergeRuleType;
import com.dazzle.asklepios.domain.enumeration.RuleSeverity;
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
import org.wildfly.common.annotation.NotNull;

import java.io.Serializable;
import java.util.Map;
@Getter
@Setter
@Entity
@Table(name = "patient_merge_validation_rules")
public class PatientMergeValidationRule implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @NotNull
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "entity_name")
    private String entityName;

    @Column(name = "table_name")
    private String tableName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private MergeRuleType ruleType;

    @NotNull
    @Column(name = "condition_config", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> conditionConfig;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false)
    private AppliesTo appliesTo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private RuleSeverity severity;

    @NotNull
    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "action_required")
    private String actionRequired;

    @NotNull
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;


}
