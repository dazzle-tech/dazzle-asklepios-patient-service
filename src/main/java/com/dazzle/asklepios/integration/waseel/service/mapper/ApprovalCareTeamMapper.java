package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.client.setup.PractitionerClient;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalCareTeam;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ApprovalCareTeamMapper {

    private static final String PRACT_SUB_SPECIALTY = "PRACT_SUB_SPECIALTY";
    private static final Logger log = LoggerFactory.getLogger(ApprovalCareTeamMapper.class);
    private final PractitionerClient practitionerClient;
    private final ApLovMapperService apLovMapperService;

    public List<WaseelApprovalCareTeam> toWaseelCareTeam(PatientEncounter encounter) {
        if (encounter == null || encounter.getPractitionerId() == null) {
            return List.of();
        }

        PractitionerDTO practitioner = getPractitioner(encounter.getPractitionerId());

        if (practitioner == null) {
            throw badRequest(
                    "Practitioner not found",
                    "practitioner.notFound"
            );
        }

        return List.of(buildPractitionerCareTeam(1, practitioner));
    }

    private WaseelApprovalCareTeam buildPractitionerCareTeam(
            Integer sequence,
            PractitionerDTO practitioner
    ) {
        String practitionerName = required(
                fullName(practitioner),
                "Practitioner name is required",
                "practitioner.name.required"
        );

        String physicianCode = required(
                firstNonBlank(
                        practitioner.defaultMedicalLicense(),
                        practitioner.secondaryMedicalLicense()
                ),
                "Practitioner medical license is required",
                "practitioner.license.required"
        );

        String practitionerRole = mapPractitionerRole(
                required(
                        practitioner.jobRole(),
                        "Practitioner job role is required",
                        "practitioner.jobRole.required"
                )
        );

        String subSpecialtyValueCode = required(
                practitioner.subSpecialty(),
                "Practitioner sub specialty is required",
                "practitioner.subSpecialty.required"
        );


        String specialityDisplay =
                apLovMapperService.getDisplayValueByLovCodeAndValueCode(
                        PRACT_SUB_SPECIALTY,
                        subSpecialtyValueCode
                );

        if (specialityDisplay == null || specialityDisplay.isBlank()) {
            specialityDisplay = WaseelPracticeCodeMapper.mapSubSpecialtyDisplay(subSpecialtyValueCode);
        }

        // If LOV doesn't contain a display value for this sub-specialty, don't fail the whole request.
        // Fall back to the raw subSpecialtyValueCode and log a warning so the data issue can be fixed
        // in the LOV configuration later.
        if (specialityDisplay == null || specialityDisplay.isBlank()) {
            log.warn("No display value found for LOV '{}' value code '{}'. Falling back to value code as display.", PRACT_SUB_SPECIALTY, subSpecialtyValueCode);
            specialityDisplay = subSpecialtyValueCode;
        }

        String specialityCode = WaseelPracticeCodeMapper.mapSubSpecialtyCode(
                subSpecialtyValueCode
        );

        String qualificationCode = WaseelPracticeCodeMapper.mapEducationCode(
                practitioner.educationalLevel(),
                specialityCode
        );

        return new WaseelApprovalCareTeam(
                sequence,
                practitionerName,
                physicianCode,
                practitionerRole,
                "primary",
                specialityDisplay,
                specialityCode,
                qualificationCode
        );
    }

    private PractitionerDTO getPractitioner(Long practitionerId) {
        try {
            return practitionerClient.getPractitioner(practitionerId);
        } catch (FeignException.NotFound ex) {
            return null;
        }
    }

    private String mapPractitionerRole(String jobRole) {
        String value = jobRole.trim();

        return switch (value) {
            case "PHYSICIAN",
                 "GENERAL_PRACTITIONER",
                 "SPECIALIST",
                 "ANESTHESIOLOGIST",
                 "RADIOLOGIST",
                 "PATHOLOGIST",
                 "PSYCHIATRIST" -> "doctor";

            case "DENTIST" -> "dentist";
            case "NURSE", "MIDWIFE" -> "nurse";
            case "PHARMACIST" -> "pharmacist";
            case "PHYSICAL_THERAPIST" -> "physio";

            default -> throw badRequest(
                    "Unsupported practitioner job role for Waseel: " + jobRole,
                    "practitioner.jobRole.unsupported"
            );
        };
    }

    private String fullName(PractitionerDTO practitioner) {
        return firstNonBlank(
                join(practitioner.firstName(), practitioner.lastName()),
                practitioner.email(),
                practitioner.defaultMedicalLicense(),
                practitioner.secondaryMedicalLicense()
        );
    }

    private String join(String first, String second) {
        String firstValue = first == null ? "" : first.trim();
        String secondValue = second == null ? "" : second.trim();
        return (firstValue + " " + secondValue).trim();
    }

    private String required(String value, String message, String errorKey) {
        if (value == null || value.isBlank()) {
            throw badRequest(message, errorKey);
        }

        return value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    private BadRequestAlertException badRequest(String message, String errorKey) {
        return new BadRequestAlertException(
                message,
                "preAuthorization",
                errorKey
        );
    }
}