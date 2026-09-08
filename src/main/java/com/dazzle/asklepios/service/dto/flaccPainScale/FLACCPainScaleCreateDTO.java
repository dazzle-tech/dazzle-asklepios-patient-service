package com.dazzle.asklepios.service.dto.flaccPainScale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FLACCPainScaleCreateDTO {

    private Long patientId;

    private Long encounterId;

    private String faceLov;

    private String legsLov;

    private String activityLov;

    private String cryLov;

    private String consolabilityLov;
}
