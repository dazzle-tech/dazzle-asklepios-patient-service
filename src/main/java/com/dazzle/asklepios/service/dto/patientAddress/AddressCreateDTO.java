package com.dazzle.asklepios.service.dto.patientAddress;

import com.dazzle.asklepios.domain.AddressLocation;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AddressCreateDTO(

        @NotNull
        AddressLocation locationJson,

        @NotNull
        String streetName,

        String houseApartmentNumber,
        String postalZipCode,
        String additionalAddressLine

) implements Serializable {
}

