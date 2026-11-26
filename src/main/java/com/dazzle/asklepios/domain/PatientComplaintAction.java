package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.PatientComplaintActionStatus;
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
import java.io.Serializable;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "patient_complaint_action")
@Getter
@Setter
public class PatientComplaintAction extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "complaint_id", nullable = false)
    private PatientComplaint complaint;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "assigned_user_id", nullable = false)
    private Long assignedUserId;

    @Column(name = "assigned_user_name")
    private String assignedUserName;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PatientComplaintActionStatus status;

    @Column(name = "result", columnDefinition = "text")
    private String result;

    @Column(name = "completed_by_user_id")
    private Long completedByUserId;

    @Column(name = "completed_by")
    private String completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Override
    public Long getId() {
        return id;
    }
}
