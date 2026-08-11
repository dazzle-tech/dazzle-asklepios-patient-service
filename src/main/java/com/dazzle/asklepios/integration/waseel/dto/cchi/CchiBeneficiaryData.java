package com.dazzle.asklepios.integration.waseel.dto.cchi;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CchiBeneficiaryData(
        String documentId,
        String documentType,

        String fullName,
        String firstName,
        @JsonProperty("middleName")
        @JsonAlias({"middle_name", "secondName", "second_name"})
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

        @JsonProperty("dob")
        @JsonAlias({"dateOfBirth", "date_of_birth"})
        String dob,
        String eHealthId,
        String nationality,
        String residencyType,

        @JsonProperty("contactNumber")
        @JsonAlias({"contact_number", "mobileNumber", "mobile_number", "primaryMobileNumber", "primary_mobile_number"})
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
        @JsonProperty("preferredLanguage")
        @JsonAlias({"PreferredLanguage", "preferred_language"})
        String preferredLanguage,
        String religion,
        String occupation,

        Long nphiesId,
        String providerId,
        Boolean isNewBorn,

        List<CchiInsurancePlan> insurancePlans
) {}