package com.dazzle.asklepios.web.rest.vm.address;

import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.domain.AddressLocation;

import java.io.Serializable;

public record AddressResponseVM(
        Long id,
        Long patientId,
        AddressLocation locationJson,
        String streetName,
        String houseApartmentNumber,
        String postalZipCode,
        String additionalAddressLine,
        Boolean isCurrent
) implements Serializable {

    public static AddressResponseVM ofEntity(Address a) {
        return new AddressResponseVM(
                a.getId(),
                a.getPatient() != null ? a.getPatient().getId() : null,
                a.getLocationJson(),
                a.getStreetName(),
                a.getHouseApartmentNumber(),
                a.getPostalZipCode(),
                a.getAdditionalAddressLine(),
                a.getIsCurrent()
        );
    }
}
