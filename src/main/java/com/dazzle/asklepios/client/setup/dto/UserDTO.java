package com.dazzle.asklepios.client.setup.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDTO(
        Long id,
        String login,
        String email,
        String firstName,
        String lastName,
        String langKey,
        String phoneNumber
) {
}
