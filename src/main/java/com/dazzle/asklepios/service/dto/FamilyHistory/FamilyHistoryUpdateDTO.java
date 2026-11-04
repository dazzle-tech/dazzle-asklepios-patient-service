package com.dazzle.asklepios.service.dto.FamilyHistory;

import com.dazzle.asklepios.domain.enumeration.Relations;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FamilyHistoryUpdateDTO(

        @NotNull
        Long id,

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