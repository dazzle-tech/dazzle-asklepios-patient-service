package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.ToothActionLogType;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "tooth_action_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToothActionLog extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "tooth_id", nullable = false)
    private Long toothId;

    @NotNull
    @Column(name = "dental_action_id", nullable = false)
    private Long dentalActionId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", nullable = false)
    private ToothActionLogType logType;

    @NotNull
    @Column(name = "log_time", nullable = false)
    private Instant logTime;

    @NotNull
    @Column(name = "log_owner", nullable = false)
    private String logOwner;
}
