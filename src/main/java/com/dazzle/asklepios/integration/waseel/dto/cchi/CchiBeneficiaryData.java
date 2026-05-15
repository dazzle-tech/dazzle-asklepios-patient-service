package com.dazzle.asklepios.integration.waseel.dto.cchi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CchiBeneficiaryData(
        String documentId,
        String documentType,
        String fullName,
        String firstName,
        String middleName,
        String lastName,
        String familyName,
        String dob,
        String gender,
        String nationality,
        String contactNumber,
        String email,
        String addressLine,
        List<CchiInsurancePlan> insurancePlans
) {}