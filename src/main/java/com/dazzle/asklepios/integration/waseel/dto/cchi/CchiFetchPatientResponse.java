package com.dazzle.asklepios.integration.waseel.dto.cchi;

import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.PatientInsurance;

import java.util.List;

public record CchiFetchPatientResponse(
        boolean alreadyExists,
        String message,
        Patient patient,
        Address address,
        PatientDocument document,
        List<PatientInsurance> insurances
) {}
