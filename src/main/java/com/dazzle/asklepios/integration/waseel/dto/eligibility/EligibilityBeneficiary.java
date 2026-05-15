package com.dazzle.asklepios.integration.waseel.dto.eligibility;

import java.util.List;

public record EligibilityBeneficiary(
        Long id,
        String name,
        String documentId,
        String documentType,
        String firstName,
        String secondName,
        String thirdName,
        String familyName,
        String fullName,
        String dob,
        String gender,
        String nationality,
        String contactNumber,
        String email,
        String emergencyPhoneNumber,
        String bloodGroup,
        String fileId,
        String eHealthId,
        String residencyType,
        String maritalStatus,
        String religion,
        String occupation,
        String preferredLanguage,
        String addressLine,
        String streetLine,
        String city,
        String state,
        String country,
        String postalCode,
        Boolean isNewBorn,
        List<EligibilityInsurancePlan> plans,
        Boolean isEligibilityDone
) {}