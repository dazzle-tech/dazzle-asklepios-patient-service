package com.dazzle.asklepios.web.rest.vm.patientPortal;

import com.dazzle.asklepios.domain.Patient;

public record PatientPortalLoginVM(
    Patient patient,
    String token
){
}
