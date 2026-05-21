package com.dazzle.asklepios.integration.waseel.dto.cchi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CchiCoverageClass(
        String type,
        String value,
        String name
) implements Serializable {
}