package com.dazzle.asklepios.web.rest.vm.observations;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
public class RespiratoryRateResponseVM {
    private Integer respiratoryRate;
    private Instant createdAt;
}
