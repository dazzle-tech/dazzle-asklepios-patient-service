package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.MergeDecision;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
@Table(name = "patient_merge_master_decisions")
public class PatientMergeMasterDecision extends AbstractAuditingEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merge_log_id", nullable = false)
    private PatientMergeLog mergeLog;

    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    @Column(name = "from_record_id")
    private Long fromRecordId;

    @Column(name = "to_record_id")
    private Long toRecordId;

    @Column(name = "match_key", length = 255)
    private String matchKey;

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "field_label", length = 150)
    private String fieldLabel;

    @Column(name = "from_value", length = 2000)
    private String fromValue;

    @Column(name = "to_value", length = 2000)
    private String toValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_decision", nullable = false, length = 50)
    private MergeDecision suggestedDecision;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_decision", length = 50)
    private MergeDecision finalDecision;

    @Column(name = "selected_value", length = 2000)
    private String selectedValue;
}