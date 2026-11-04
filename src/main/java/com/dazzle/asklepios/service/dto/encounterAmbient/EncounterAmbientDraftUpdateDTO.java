package com.dazzle.asklepios.service.dto.encounterAmbient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EncounterAmbientDraftUpdateDTO(@NotNull JsonNode draft) implements Serializable {
}
