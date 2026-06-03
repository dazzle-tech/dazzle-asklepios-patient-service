package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ICDDiagnosisDTO(
        Long id,
        String icdDiagnosisUid,
        String icdCode,
        String icdCoding,
        String categoryCode,
        String icdShortDescription,
        String icdFullDescription
) {}
