package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "encounter_amendment_change_log")
@Getter
@Setter
@NoArgsConstructor
public class EncounterAmendmentChangeLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reopen_session_id", nullable = false)
    private Long reopenSessionId;

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "change_data", columnDefinition = "jsonb", nullable = false)
    private String changeData;

    @Column(name = "changed_by", length = 50)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}
