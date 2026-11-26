package com.dazzle.asklepios.service.dto.patientComplaint;

import com.dazzle.asklepios.domain.enumeration.PatientComplaintFeedbackType;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintPreferredContactMethod;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatientComplaintMobileSubmitDTO {

    @NotNull
    private Boolean consentToContact;

    @NotNull
    private Long facilityId;

    @NotNull
    private Long departmentId;

    @NotNull
    private PatientComplaintFeedbackType feedbackType;

    private PatientComplaintType complaintType;

    @NotNull
    private LocalDate incidentDate;

    @NotNull
    private LocalTime incidentTime;

    @NotBlank
    private String description;

    @Size(max = 500)
    private String involvedStaffNames;

    private String desiredResolution;

    @NotNull
    private PatientComplaintPreferredContactMethod preferredContactMethod;
}
