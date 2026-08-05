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

    private static final Logger log = LoggerFactory.getLogger(ApprovalCareTeamMapper.class);

    private final PractitionerClient practitionerClient;
    private final ApLovMapperService apLovMapperService;

    public List<WaseelApprovalCareTeam> toWaseelCareTeam(PatientEncounter encounter) {
        if (encounter == null) {
            throw badRequest(
                    "Encounter is required to build care team",
                    "careTeam.encounter.required"
            );
        }

        if (encounter.getPractitionerId() == null && isBlank(encounter.getStartedBy())) {
            throw badRequest(
                    "Practitioner id or startedBy is required before Waseel pre-authorization",
                    "careTeam.practitioner.required"
            );
        }

        PractitionerDTO practitioner = resolvePractitioner(encounter);

        if (practitioner == null) {
            throw badRequest(
                    "Practitioner not found",
                    "practitioner.notFound"
            );
        }

        return List.of(buildPractitionerCareTeam(1, practitioner));
    }

    private PractitionerDTO resolvePractitioner(PatientEncounter encounter) {
        try {
            Long practitionerId = encounter.getPractitionerId();
            String login = normalizeBlankToNull(encounter.getStartedBy());

            return practitionerClient.resolvePractitioner(practitionerId, login);

        } catch (FeignException.NotFound ex) {
            return null;
        } catch (FeignException ex) {
            throw badRequest(
                    "Unable to fetch practitioner from setup service",
                    "practitioner.fetch.failed"
            );
        }
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

        String subSpecialtyRaw = required(
                practitioner.subSpecialty(),
                "Practitioner sub specialty is required",
                "practitioner.subSpecialty.required"
        );

        // Practitioner stores LOV key (e.g. 515674776343000); Waseel needs SUB_SPC_XXX.
        String subSpecialtyValueCode = apLovMapperService.resolvePractSubSpecialtyValueCode(subSpecialtyRaw);
        if (isBlank(subSpecialtyValueCode)) {
            throw badRequest(
                    "Unknown practitioner sub specialty LOV value: " + subSpecialtyRaw
                            + ". Expected PRACT_SUB_SPECIALTY key or SUB_SPC_XXX value code.",
                    "practitioner.subSpecialty.invalid"
            );
        }

        log.info(
                "[PREAUTH_CARE_TEAM] Resolved sub specialty. raw={} valueCode={}",
                subSpecialtyRaw,
                subSpecialtyValueCode
        );

        String specialityCode = required(
                WaseelPracticeCodeMapper.mapSubSpecialtyCode(subSpecialtyValueCode),
                "Practitioner specialty code is required",
                "practitioner.specialityCode.required"
        );

        String specialityDisplay = firstNonBlank(
                apLovMapperService.getDisplayValueByLovCodeAndKey(
                        AsklepiosLovCodes.PRACT_SUB_SPECIALTY,
                        subSpecialtyRaw
                ),
                apLovMapperService.getDisplayValueByLovCodeAndValueCode(
                        AsklepiosLovCodes.PRACT_SUB_SPECIALTY,
                        subSpecialtyValueCode
                ),
                WaseelPracticeCodeMapper.mapSubSpecialtyDisplay(subSpecialtyValueCode)
        );

        if (isBlank(specialityDisplay)) {
            log.warn(
                    "No display value found for LOV '{}' value code '{}'. Falling back to specialty code '{}'.",
                    AsklepiosLovCodes.PRACT_SUB_SPECIALTY,
                    subSpecialtyValueCode,
                    specialityCode
            );
            specialityDisplay = specialityCode;
        }

        String qualificationCode = required(
                WaseelPracticeCodeMapper.mapEducationCode(
                        practitioner.educationalLevel(),
                        specialityCode
                ),
                "Practitioner qualification code is required",
                "practitioner.qualificationCode.required"
        );

        return new WaseelApprovalCareTeam(
                sequence,
                practitionerName,
                physicianCode,
                practitionerRole,
                "primary",
                specialityDisplay.trim(),
                specialityCode,
                qualificationCode
        );
    }

    private String mapPractitionerRole(String jobRole) {
        String value = jobRole.trim().toUpperCase();

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
        if (practitioner == null) {
            return null;
        }

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
        String value = (firstValue + " " + secondValue).trim();

        return value.isBlank() ? null : value;
    }

    private String normalizeBlankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String required(String value, String message, String errorKey) {
        if (isBlank(value)) {
            throw badRequest(message, errorKey);
        }

        return value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private BadRequestAlertException badRequest(String message, String errorKey) {
        return new BadRequestAlertException(
                message,
                "preAuthorization",
                errorKey
        );
    }
}