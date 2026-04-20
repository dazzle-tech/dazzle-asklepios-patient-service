package com.dazzle.asklepios.client.setup.dto;

import com.dazzle.asklepios.domain.DiagnosticTest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogDiagnosticTestDTO(
         DiagnosticTestSetupDTO test
) {
}
