package com.dazzle.asklepios.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EncounterDashboardPreferenceSaveDTO(
    @NotNull(message = "widgetKeys is required") List<String> widgetKeys
) implements Serializable {
}
