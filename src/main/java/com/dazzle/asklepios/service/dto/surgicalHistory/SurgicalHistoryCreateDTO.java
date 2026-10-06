package com.dazzle.asklepios.service.dto.surgicalHistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SurgicalHistoryCreateDTO(

        @NotNull
        Long patientId,

        String surgery,

        Date dateOfSurgery,

        String facility,

        String anesthesiaType,

        String complications,

        String adverseReactionsToAnesthesia,

        Boolean hasImplantsOrDevices,

        String implantsOrDevicesDescription,

        @NotNull
        Boolean patientIsFree,

        String freeText

) implements Serializable {
}