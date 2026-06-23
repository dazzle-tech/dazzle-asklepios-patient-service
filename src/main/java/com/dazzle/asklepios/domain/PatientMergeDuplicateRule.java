package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientMergeDuplicateAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "patient_merge_duplicate_rules")
public class PatientMergeDuplicateRule  {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_config_id", nullable = false)
    private PatientMergeTableConfig tableConfig;

    @Column(name = "condition_column", nullable = false, length = 100)
    private String conditionColumn;

    @Column(name = "from_value", nullable = false, length = 100)
    private String fromValue;

    @Column(name = "to_value", nullable = false, length = 100)
    private String toValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private PatientMergeDuplicateAction action;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;


}