package com.dazzle.asklepios.web.rest.vm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Minimal public autofill payload for the patient-complaint form. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatientComplaintPublicLookupVM {

    private String patientName;
    private String contactPhone;
    private String contactEmail;
}
