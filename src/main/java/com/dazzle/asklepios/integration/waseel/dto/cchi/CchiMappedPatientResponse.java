package com.dazzle.asklepios.integration.waseel.dto.cchi;

import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;

public record CchiMappedPatientResponse(
        Patient patient,
        Address address,
        PatientDocument document
) {}