package com.dazzle.asklepios.integration.waseel.mapper;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityBeneficiary;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCoverageClass;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityInsurancePlan;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityRequest;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityTestRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WaseelEligibilityMapper {

    public EligibilityRequest toRequest(
            Patient patient,
            PatientEncounter encounter,
            EligibilityTestRequest request
    ) {
        EligibilityInsurancePlan insurancePlan = buildTemporaryInsurancePlan();

        EligibilityBeneficiary beneficiary = new EligibilityBeneficiary(
                patient.getId(),
                buildFullName(patient),
                getDocumentId(patient),
                "NI",
                patient.getFirstName(),
                patient.getSecondName(),
                patient.getThirdName(),
                patient.getLastName(),
                buildFullName(patient),
                patient.getDateOfBirth() != null ? patient.getDateOfBirth().toString() : null,
                patient.getSexAtBirth() != null ? patient.getSexAtBirth().name() : null,
                patient.getNationality(),
                patient.getPrimaryMobileNumber(),
                patient.getEmail(),
                null,
                null,
                patient.getMedicalRecordNumber(),
                null,
                null,
                patient.getMaritalStatus(),
                patient.getReligion(),
                patient.getOccupation(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                List.of(insurancePlan),
                true
        );

        return new EligibilityRequest(
                false,
                beneficiary,
                null,
                insurancePlan,
                request.serviceDate(),
                null,
                false,
                false,
                true,
                false,
                Boolean.TRUE.equals(request.isEmergency()),
                Boolean.TRUE.equals(request.referral()),
                Map.of(),
                "-1"
        );
    }

    private EligibilityInsurancePlan buildTemporaryInsurancePlan() {
        EligibilityCoverageClass coverageClass = new EligibilityCoverageClass(
                "plan",
                "a",
                ""
        );

        return new EligibilityInsurancePlan(
                "3793",
                "INS-FHIR",
                "Insurance Company Testing Payer",
                "12121212",
                "357159456",
                "INS-FHIR",
                "-1",
                "2028-09-28",
                "other",
                "EHCPOL",
                null,
                null,
                List.of(coverageClass),
                "123456789",
                true
        );
    }

    private String buildFullName(Patient patient) {
        return String.join(" ",
                value(patient.getFirstName()),
                value(patient.getSecondName()),
                value(patient.getThirdName()),
                value(patient.getLastName())
        ).trim().replaceAll("\\s+", " ");
    }

    private String value(String text) {
        return text == null ? "" : text;
    }

    private String getDocumentId(Patient patient) {
        if (patient.getPatientDocuments() == null || patient.getPatientDocuments().isEmpty()) {
            return null;
        }

        return patient.getPatientDocuments()
                .stream()
                .findFirst()
                .map(document -> document.getPatient().getPatientDocuments().stream()
                        .filter(doc -> doc.getType() != null && doc.getType().equals(DocumentType.NATIONAL_ID))
                        .findFirst()
                        .map(doc -> doc.getNumber())
                        .orElse(null)
                )
                .orElse(null);
    }
}