package com.dazzle.asklepios.service.dto.surgicalHistory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.io.Serializable;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SurgicalHistoryUpdateDTO(

        @NotNull
        Long id,

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
      Boolean patientIsFree

) implements Serializable {
}
