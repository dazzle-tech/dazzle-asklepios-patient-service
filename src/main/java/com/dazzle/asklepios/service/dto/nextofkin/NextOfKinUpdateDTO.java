package com.dazzle.asklepios.service.dto.nextofkin;


import com.dazzle.asklepios.domain.enumeration.RelationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * DTO for updating Next of Kin
 */
public record NextOfKinUpdateDTO(
        @NotNull Long id,
        @NotNull Long patientId,
        @NotEmpty String name,
        @NotNull RelationType relationship,
        @NotEmpty String address,
        @NotEmpty @Email String email,
        @NotEmpty String mobileNumber,
        String telephone,
        String internationalNumber,
        String landlineNumber
) implements Serializable {
}
