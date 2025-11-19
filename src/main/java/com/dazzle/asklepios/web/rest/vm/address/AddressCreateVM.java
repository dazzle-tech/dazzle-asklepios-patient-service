package com.dazzle.asklepios.web.rest.vm.address;

import com.dazzle.asklepios.domain.Address;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AddressCreateVM(
        String country,
        String stateProvince,
        String city,
        String streetName,
        String houseApartmentNumber,
        String postalZipCode,
        String additionalAddressLine,
        String countryId,
        Boolean isCurrent // السيرفس رح يتجاهلها ويخلي الجديد دايمًا current، بس خليّناها لو احتجتها مستقبلاً
) implements Serializable {

    public static AddressCreateVM ofEntity(Address address) {
        return new AddressCreateVM(
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
