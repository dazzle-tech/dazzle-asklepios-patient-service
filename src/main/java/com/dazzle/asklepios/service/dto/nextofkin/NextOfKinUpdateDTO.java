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
        @NotEmpty(message = "Name can not be Empty")
        String name,
        @NotNull(message = "Relationship can not be Empty")
        RelationType relationship,
        @NotEmpty(message = "Address can not be Empty")
        String address,
        @NotEmpty(message = "Email can not be Empty")
        @Email String email,
        @NotEmpty(message = "Mobile Number can not be Empty")
        String mobileNumber,
        String telephone,
        String internationalNumber,
        String landlineNumber
) implements Serializable {
}
