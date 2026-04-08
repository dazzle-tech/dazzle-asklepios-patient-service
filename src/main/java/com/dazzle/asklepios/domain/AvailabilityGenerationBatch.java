package com.dazzle.asklepios.domain;

import com.dazzle.asklepios.domain.enumeration.AvailabilityGenerationScope;
import com.dazzle.asklepios.domain.enumeration.BatchStatus;
import com.dazzle.asklepios.domain.enumeration.HolidayHandlingMode;
import com.dazzle.asklepios.domain.enumeration.TemplateType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "availability_generation_batch")
@Getter
@Setter
public class AvailabilityGenerationBatch extends  AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "template_id", nullable = false)
    private AvailabilityTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_handling_mode")
    private HolidayHandlingMode holidayHandlingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private AvailabilityGenerationScope scope;

    @Column(name = "apply_start_date_time", nullable = false)
    private Instant applyStartDateTime;

    @Column(name = "apply_end_date_time", nullable = false)
    private Instant applyEndDateTime;

    @Column(name = "total_slots")
    private Integer totalSlots;

    @Column(name = "daily_avg")
    private Integer dailyAvg;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false)
    private BatchStatus executionStatus;

}