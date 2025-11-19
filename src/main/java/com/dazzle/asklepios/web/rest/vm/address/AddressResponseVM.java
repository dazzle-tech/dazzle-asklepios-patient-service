package com.dazzle.asklepios.web.rest.vm.address;

import com.dazzle.asklepios.domain.Address;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
@JsonIgnoreProperties(ignoreUnknown = true)

public record AddressResponseVM(
        Long id,
        Long patientId,
        String country,
        String stateProvince,
        String city,
        String streetName,
        String houseApartmentNumber,
        String postalZipCode,
        String additionalAddressLine,
        String countryId,
        Boolean isCurrent
) implements Serializable {

    public static AddressResponseVM ofEntity(Address address) {
        return new AddressResponseVM(
                address.getId(),
                address.getPatient() != null ? address.getPatient().getId() : null,
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
