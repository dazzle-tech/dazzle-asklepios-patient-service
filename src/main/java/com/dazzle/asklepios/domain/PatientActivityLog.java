package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientActivityAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * One change to a patient activity; createdBy and createdDate say who made it and when.
 */
@Entity
@Table(name = "patient_activity_log")
@Getter
@Setter
@NoArgsConstructor
public class PatientActivityLog extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private PatientActivityAction action;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    public PatientActivityLog(Long activityId, PatientActivityAction action, String oldValue, String newValue) {
        this.activityId = activityId;
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
}
