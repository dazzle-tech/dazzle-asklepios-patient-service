package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "patient_merge_item_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientMergeItemLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merge_log_id", nullable = false)
    private Long mergeLogId;

    @Column(name = "entity_name", nullable = false)
    private String entityName;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "old_patient_id", nullable = false)
    private Long oldPatientId;

    @Column(name = "new_patient_id", nullable = false)
    private Long newPatientId;

    @Column(name = "moved_at")
    private Instant movedAt;

    @Column(name = "record_updated_at_at_merge")
    private Instant recordUpdatedAtAtMerge;
}