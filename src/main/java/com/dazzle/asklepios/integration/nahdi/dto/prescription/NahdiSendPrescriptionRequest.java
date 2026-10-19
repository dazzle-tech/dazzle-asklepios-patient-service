package com.dazzle.asklepios.integration.nahdi.dto.prescription;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

/**
 * Request body of Nahdi {@code SendPrescription_clinics}.
 */
@Builder
public record NahdiSendPrescriptionRequest(
        @JsonProperty("pres_Head") NahdiPrescriptionHead presHead,
        @JsonProperty("pres_Diagnosis") List<NahdiPrescriptionDiagnosis> presDiagnosis,
        @JsonProperty("pres_Item") List<NahdiPrescriptionItem> presItem
) {}
