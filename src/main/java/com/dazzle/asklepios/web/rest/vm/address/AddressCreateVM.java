package com.dazzle.asklepios.web.rest.vm.address;

import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.domain.AddressLocation;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AddressCreateVM(
        @NotNull AddressLocation locationJson,
        @NotNull String streetName,
        String houseApartmentNumber,
        String postalZipCode,
        String additionalAddressLine
) implements Serializable {

    public static AddressCreateVM ofEntity(Address address) {
        return new AddressCreateVM(
                address.getLocationJson(),
                address.getStreetName(),
                address.getHouseApartmentNumber(),
                address.getPostalZipCode(),
                address.getAdditionalAddressLine()
        );
    }
}
