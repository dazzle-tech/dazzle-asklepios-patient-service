package com.dazzle.asklepios.service.dto.radiology;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PacsStudyDTO(

        String study,

        @JsonProperty("study_description")
        String studyDescription,

        @JsonProperty("study_date")
        String studyDate,

        String modality,

        @JsonProperty("accession_number")
        String accessionNumber,

        String link,

        @JsonProperty("expires_at")
        String expiresAt,

        PacsPatientDTO patient

) {
}