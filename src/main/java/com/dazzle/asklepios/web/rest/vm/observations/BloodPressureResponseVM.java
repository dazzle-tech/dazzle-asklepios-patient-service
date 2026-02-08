package com.dazzle.asklepios.web.rest.vm.observations;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
public class BloodPressureResponseVM {
    private Integer systolic;
    private Integer diastolic;
    private Instant createdAt;
}
