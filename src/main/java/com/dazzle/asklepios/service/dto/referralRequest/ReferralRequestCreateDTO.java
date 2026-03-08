package com.dazzle.asklepios.service.dto.referralRequest;

import com.dazzle.asklepios.domain.enumeration.ReferralPriority;
import com.dazzle.asklepios.domain.enumeration.ReferralStatus;
import com.dazzle.asklepios.domain.enumeration.ReferralType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

public record ReferralRequestCreateDTO(

        @NotNull Long patientId,
        Long encounterId,

        @NotNull ReferralType referralType,

        @NotNull Long fromFacilityId,
        @NotNull Long toFacilityId,

        @NotNull Long fromDepartmentId,
        @NotNull Long toDepartmentId,

        @NotBlank
        String referralReason,

        @NotNull ReferralPriority priority,


        @NotNull ReferralStatus status

) implements Serializable {
}
