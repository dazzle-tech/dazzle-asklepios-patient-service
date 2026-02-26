package com.dazzle.asklepios.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@jakarta.persistence.Table(name = "ap_resource_availability_slice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ResourceAvailabilitySlice implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "key")
    private String key;

    @NotNull
    @Column(name = "resource_key", nullable = false)
    private String resourceKey;

    @Column(name = "facility_key")
    private String facilityKey;

    @Column(name = "department_key")
    private String departmentKey;

    @NotNull
    @Column(name = "day_of_week", nullable = false)
    private String dayOfWeek; // 0=Saturday, 1=Sunday, ..., 6=Friday

    @NotNull
    @Column(name = "start_time_minutes", nullable = false)
    private String startTimeMinutes; // Time in minutes from midnight

    @NotNull
    @Column(name = "end_time_minutes", nullable = false)
    private String endTimeMinutes; // Time in minutes from midnight

    @Column(name = "slice_duration_minutes")
    private String sliceDurationMinutes;

    @Column(name = "isbocked")
    private String isBlocked;

    @Column(name = "isbreak")
    private Boolean isBreak = false;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "deleted_by")
    private String deletedBy;

    @Column(name = "created_at")
    private BigDecimal createdAt;

    @Column(name = "updated_at")
    private BigDecimal updatedAt;

    @Column(name = "deleted_at")
    private BigDecimal deletedAt;

    @Column(name = "is_valid")
    private Boolean isValid = true;
}

