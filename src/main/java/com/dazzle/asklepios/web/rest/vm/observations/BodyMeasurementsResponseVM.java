package com.dazzle.asklepios.web.rest.vm.observations;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BodyMeasurementsResponseVM {

    private BigDecimal weight;

    private BigDecimal height;

    private Instant createdAt;

    private String createdBy;

    private long encounterId;

    private String encounterNumber;

    private Boolean isActive;

}
