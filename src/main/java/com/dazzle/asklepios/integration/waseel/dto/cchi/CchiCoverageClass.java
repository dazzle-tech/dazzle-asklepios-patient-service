package com.dazzle.asklepios.integration.waseel.dto.cchi;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CchiCoverageClass(
        @JsonAlias({"classType"})
        String type,
        @JsonAlias({"classValue"})
        String value,
        @JsonAlias({"className"})
        String name
) implements Serializable {
}