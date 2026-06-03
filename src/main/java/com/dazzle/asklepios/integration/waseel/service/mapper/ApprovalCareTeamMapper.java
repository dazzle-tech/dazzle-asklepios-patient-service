package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.client.setup.PractitionerClient;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalCareTeam;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class ApprovalCareTeamMapper {

    private final PractitionerClient practitionerClient;

    public List<WaseelApprovalCareTeam> toWaseelCareTeam(PatientEncounter encounter) {
        if (encounter == null) {
            return List.of();
        }

        List<WaseelApprovalCareTeam> careTeam = new ArrayList<>();
        AtomicInteger sequence = new AtomicInteger(1);

        if (isNotBlank(encounter.getStartedBy())) {
            careTeam.add(new WaseelApprovalCareTeam(
                    sequence.getAndIncrement(),
                    encounter.getStartedBy(),
                    encounter.getStartedBy(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "started_by"
            ));
        }

        if (isNotBlank(encounter.getCompletedBy())) {
            careTeam.add(new WaseelApprovalCareTeam(
                    sequence.getAndIncrement(),
                    encounter.getCompletedBy(),
                    encounter.getCompletedBy(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "completed_by"
            ));
        }

        if (encounter.getPractitionerId() != null) {
            PractitionerDTO practitioner = getPractitioner(encounter.getPractitionerId());

            careTeam.add(buildPractitionerCareTeam(
                    sequence.getAndIncrement(),
                    encounter.getPractitionerId(),
                    practitioner
            ));
        }

        return careTeam;
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
                    "",
                    "primary",
                    "",
                    "",
                    "",
                    "practitioner"
            );
        }

        return new WaseelApprovalCareTeam(
                sequence,
                fullName(practitioner),
                firstNonBlank(
                        practitioner.defaultMedicalLicense(),
                        practitioner.secondaryMedicalLicense(),
                        practitioner.email(),
                        String.valueOf(practitioner.id())
                ),
                safe(practitioner.jobRole()),
                "primary",
                firstNonBlank(
                        practitioner.subSpecialty(),
                        practitioner.specialty()
                ),
                safe(practitioner.specialty()),
                firstNonBlank(
                        practitioner.educationalLevel(),
                        practitioner.specialty()
                ),
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
        String firstValue = safe(first);
        String secondValue = safe(second);

        String joined = (firstValue + " " + secondValue).trim();
        return joined;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}