package com.dazzle.asklepios.service.dto.FamilyHistory;

import com.dazzle.asklepios.domain.enumeration.Relations;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FamilyHistoryCreateDTO(

        @NotNull
        Long patientId,

        String condition,

        Relations relation,

        Boolean inheritedDiseases,

        @NotNull
        Boolean patientIsFree,

        String freeText

) implements Serializable {
}