package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.client.setup.PractitionerClient;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalCareTeam;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ApprovalCareTeamMapper {

    private final PractitionerClient practitionerClient;

    public List<WaseelApprovalCareTeam> toWaseelCareTeam(PatientEncounter encounter) {
        if (encounter == null || encounter.getPractitionerId() == null) {
            return List.of();
        }

        PractitionerDTO practitioner = getPractitioner(encounter.getPractitionerId());

        return List.of(
                buildPractitionerCareTeam(1, encounter.getPractitionerId(), practitioner)
        );
    }

    private WaseelApprovalCareTeam buildPractitionerCareTeam(
            Integer sequence,
            Long practitionerId,
            PractitionerDTO practitioner
    ) {
        if (practitioner == null) {
            return new WaseelApprovalCareTeam(
                    sequence,
                    String.valueOf(practitionerId),
                    String.valueOf(practitionerId),
                    null,
                    "primary",
                    null,
                    null,
                    null,
                    "practitioner"
            );
        }

        return new WaseelApprovalCareTeam(
                sequence,
                fullName(practitioner),
                firstNonBlank(
                        practitioner.defaultMedicalLicense(),
                        practitioner.secondaryMedicalLicense()
                ),
                emptyToNull(practitioner.jobRole()),
                "primary",
                emptyToNull(firstNonBlank(
                        practitioner.subSpecialty(),
                        practitioner.specialty()
                )),
                emptyToNull(practitioner.specialty()),
                emptyToNull(firstNonBlank(
                        practitioner.educationalLevel(),
                        practitioner.specialty()
                )),
                "practitioner"
        );
    }

    private PractitionerDTO getPractitioner(Long practitionerId) {
        try {
            return practitionerClient.getPractitioner(practitionerId);
        } catch (FeignException.NotFound ex) {
            return null;
        }
    }

    private String fullName(PractitionerDTO practitioner) {
        return firstNonBlank(
                join(practitioner.firstName(), practitioner.lastName()),
                practitioner.email(),
                String.valueOf(practitioner.id())
        );
    }

    private String join(String first, String second) {
        String firstValue = first == null ? "" : first;
        String secondValue = second == null ? "" : second;
        return (firstValue + " " + secondValue).trim();
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

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}