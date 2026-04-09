package com.dazzle.asklepios.web.rest.vm.observations;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
public class OxygenSaturationResponseVM {
    private BigDecimal oxygenSaturation;
    private Instant createdAt;
}
