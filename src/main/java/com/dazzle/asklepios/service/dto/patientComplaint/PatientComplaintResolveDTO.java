package com.dazzle.asklepios.service.dto.patientComplaint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatientComplaintResolveDTO {

    @NotBlank
    private String resolutionSummary;
}
