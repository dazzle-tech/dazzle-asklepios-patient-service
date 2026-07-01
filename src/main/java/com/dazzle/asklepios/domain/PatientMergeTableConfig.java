package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientMergeCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serial;
import java.io.Serializable;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
@Table(name = "patient_merge_table_configs")
public class PatientMergeTableConfig  implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_name", nullable = false, length = 100)
    private String entityName;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    @Column(name = "primary_key_column_name", nullable = false, length = 100)
    private String primaryKeyColumnName;

    @Column(name = "patient_column_name", nullable = false, length = 100)
    private String patientColumnName;

    @Column(name = "updated_at_column_name", length = 100)
    private String updatedAtColumnName;

    @Column(name = "match_key_columns", length = 500)
    private String matchKeyColumns;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;


    @Column(name = "excluded_columns", length = 1000)
    private String excludedColumns;

    @Enumerated(EnumType.STRING)
    @Column(name = "merge_category", nullable = false, length = 50)
    private PatientMergeCategory mergeCategory;
}
