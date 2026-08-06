package com.dazzle.asklepios.service.dto.radiology;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PacsPatientDTO(

        String id,

        @JsonProperty("first_name")
        String firstName,

        @JsonProperty("second_name")
        String secondName,

        @JsonProperty("last_name")
        String lastName

) {
}