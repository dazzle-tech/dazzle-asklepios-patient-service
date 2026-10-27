package com.dazzle.asklepios.service.dto.patientComplaint;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatientComplaintDeadlineDTO {

    @NotNull
    private Instant targetDeadline;
}
