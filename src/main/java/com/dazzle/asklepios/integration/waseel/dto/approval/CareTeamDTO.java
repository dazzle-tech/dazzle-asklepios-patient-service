package com.dazzle.asklepios.integration.waseel.dto.approval;

public record CareTeamDTO(
        Integer sequence,
        String practitionerName,
        String physicianCode,
        String practitionerRole,
        String careTeamRole,
        String speciality,
        String specialityCode,
        String qualificationCode
) {}