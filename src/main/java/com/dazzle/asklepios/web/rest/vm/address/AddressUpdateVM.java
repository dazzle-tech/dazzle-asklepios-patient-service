package com.dazzle.asklepios.web.rest.vm.address;

import com.dazzle.asklepios.domain.Address;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddressUpdateVM(
        @NotNull Long id,
        String country,
        String stateProvince,
        String city,
        String streetName,
        String houseApartmentNumber,
        String postalZipCode,
        String additionalAddressLine,
        String countryId,
        @NotNull Boolean isCurrent
) implements Serializable {

    public static AddressUpdateVM ofEntity(Address address) {
        return new AddressUpdateVM(
                address.getId(),
                address.getCountry(),
                address.getStateProvince(),
                address.getCity(),
                address.getStreetName(),
                address.getHouseApartmentNumber(),
                address.getPostalZipCode(),
                address.getAdditionalAddressLine(),
                address.getCountryId(),
                address.getIsCurrent()
        );
    }
}
