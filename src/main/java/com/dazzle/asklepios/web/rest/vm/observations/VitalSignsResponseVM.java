package com.dazzle.asklepios.web.rest.vm.observations;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class VitalSignsResponseVM {

    private BigDecimal temperature;

    private Integer pulseRate;

    private Integer respiratoryRate;

    private Integer bloodPressureSystolic;

    private Integer bloodPressureDiastolic;

    private BigDecimal oxygenSaturation;

    private Instant createdAt;

}
