package com.dazzle.asklepios.service.dto.patientComplaint;

import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintFeedbackType;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintPreferredContactMethod;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
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
public class PatientComplaintSubmitDTO {

    @NotNull
    private Boolean anonymous;

    @Size(max = 255)
    private String patientName;

    /**
     * Legacy free-text identifier. Prefer documentType + documentNumber;
     * when those are set, this is derived as the document number for search/display.
     */
    @Size(max = 100)
    private String patientIdentifier;

    private DocumentType documentType;

    @Size(max = 100)
    private String documentNumber;

    @Email
    @Size(max = 255)
    private String contactEmail;

    @Size(max = 50)
    private String contactPhone;

    private PatientComplaintPreferredContactMethod preferredContactMethod;

    private Boolean onBehalf;

    /** Free-text relationship when submitting on behalf of someone else. */
    @Size(max = 100)
    private String relationshipToPatient;

    @NotNull
    private Boolean consentToContact;

    @NotNull
    private Long facilityId;

    private Long departmentId;

    @NotNull
    private PatientComplaintFeedbackType feedbackType;

    /** Optional category (Medical Care, Billing, …); kept separate from feedbackType. */
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
}
