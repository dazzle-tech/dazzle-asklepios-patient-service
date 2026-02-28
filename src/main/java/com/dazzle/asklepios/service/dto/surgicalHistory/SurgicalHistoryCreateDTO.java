package com.dazzle.asklepios.service.dto.surgicalHistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.io.Serializable;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SurgicalHistoryCreateDTO(

        @NotNull
        Long patientId,

        @NotNull
        @NotBlank
        String surgery,

        @NotNull
        @PastOrPresent
        Date dateOfSurgery,

        @NotNull
        @NotBlank
        String facility,

        @NotNull
        String anesthesiaType,

        String complications,

        String adverseReactionsToAnesthesia,

        Boolean hasImplantsOrDevices,

        String implantsOrDevicesDescription

) implements Serializable {
}
