package com.dazzle.asklepios.service.dto.billing;


import jakarta.validation.constraints.NotNull;

import java.util.Date;

public record ClaimEncounterCopySurgicalHistoryCreateDTO(
        String surgery,
        Date dateOfSurgery,
        String facility,
        String anesthesiaType,
        String complications,
        String adverseReactionsToAnesthesia,
        Boolean hasImplantsOrDevices,
        String implantsOrDevicesDescription,
        @NotNull Boolean patientIsFree,
        String freeText
) {
}