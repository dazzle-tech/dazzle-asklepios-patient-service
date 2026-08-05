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

        String beneficiaryFileId,
        String systemType,
        String passportNumber,
        String borderNumber,
        String visaNumber,
        String visaType,
        String visitTitle,
        String visaExpiryDate,

        String dob,
        String eHealthId,
        String nationality,
        String residencyType,

        String contactNumber,
        String email,
        String emergencyNumber,

        String addressLine,
        String streetLine,
        String city,
        String state,
        String country,
        String postalCode,

        String martialStatus,
        String gender,
        String bloodGroup,
        String preferredLanguage,
        String religion,
        String occupation,

        Long nphiesId,
        String providerId,
        Boolean isNewBorn,

        List<CchiInsurancePlan> insurancePlans
) {}