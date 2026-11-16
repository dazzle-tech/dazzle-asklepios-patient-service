package com.dazzle.asklepios.web.rest.vm;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Public enum options for the anonymous patient-complaint web form. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatientComplaintPublicFormLookupsVM {

    private List<String> feedbackTypes;
    private List<String> complaintTypes;
    private List<String> preferredContactMethods;
    private List<String> documentTypes;
}
