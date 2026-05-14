package com.dazzle.asklepios.integration.waseel.dto.cchi;

import java.util.List;

public record CchiInquiryResponse(
        String documentId,
        String documentType,
        String fullName,
        String firstName,
        String secondName,
        String thirdName,
        String familyName,
        String dob,
        String gender,
        String nationality,
        String contactNumber,
        String email,
        String addressLine,
        List<CchiInsurancePlan> insurancePlans
) {}