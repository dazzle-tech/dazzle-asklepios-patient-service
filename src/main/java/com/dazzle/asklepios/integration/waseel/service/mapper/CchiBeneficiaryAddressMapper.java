package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.domain.AddressLocation;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.CountryName;
import com.dazzle.asklepios.integration.waseel.client.CountrySetupClient;
import com.dazzle.asklepios.integration.waseel.client.dto.CountryDistrictResponseVM;
import com.dazzle.asklepios.integration.waseel.client.dto.CountryResponseVM;
import com.dazzle.asklepios.integration.waseel.client.dto.DistrictCommunityResponseVM;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiBeneficiaryData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CchiBeneficiaryAddressMapper {

    private final CountrySetupClient countrySetupClient;
    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 1);

    public Address toAddress(CchiBeneficiaryData b) {
        if (b == null || !hasAnyAddressData(b)) {
            return null;
        }

        AddressLocation location = new AddressLocation();

        AddressLocation.Country country = resolveCountry(b.country());
        location.setCountry(country);

        AddressLocation.District district = resolveDistrict(
                country != null ? country.getId() : null,
                b.state()
        );
        location.setDistrict(district);

        AddressLocation.Community community = resolveCommunity(
                district != null ? district.getId() : null,
                b.city()
        );
        location.setCommunity(community);

        Address address = new Address();

        address.setId(null);
        address.setPatient(null);
        address.setLocationJson(location);
        address.setStreetName(resolveStreetName(b));
        address.setHouseApartmentNumber(null);
        address.setPostalZipCode(clean(b.postalCode()));
        address.setAdditionalAddressLine(clean(b.addressLine()));
        address.setIsCurrent(true);

        return address;
    }

    private AddressLocation.Country resolveCountry(String waseelCountry) {
        CountryName countryName = mapCountryToEnum(waseelCountry);

        if (countryName == null) {
            return null;
        }

        List<CountryResponseVM> countries =
                countrySetupClient.getCountryByName(countryName, FIRST_PAGE);

        CountryResponseVM country = countries.stream()
                .filter(c -> Boolean.TRUE.equals(c.isActive()))
                .findFirst()
                .orElse(null);

        if (country == null) {
            return null;
        }

        return new AddressLocation.Country(
                country.id(),
                country.name() != null ? country.name().name() : null,
                country.code()
        );
    }

    private AddressLocation.District resolveDistrict(Long countryId, String waseelState) {
        if (countryId == null || isBlank(waseelState)) {
            return null;
        }

        List<CountryDistrictResponseVM> districts =
                countrySetupClient.getDistrictByName(countryId, clean(waseelState), FIRST_PAGE);

        CountryDistrictResponseVM district = districts.stream()
                .filter(d -> Boolean.TRUE.equals(d.isActive()))
                .findFirst()
                .orElse(null);

        if (district == null) {
            return null;
        }

        return new AddressLocation.District(
                district.id(),
                district.name(),
                district.code()
        );
    }

    private AddressLocation.Community resolveCommunity(Long districtId, String waseelCity) {
        if (districtId == null || isBlank(waseelCity)) {
            return null;
        }

        List<DistrictCommunityResponseVM> communities =
                countrySetupClient.getCommunityByName(districtId, clean(waseelCity), FIRST_PAGE);

        DistrictCommunityResponseVM community = communities.stream()
                .filter(c -> Boolean.TRUE.equals(c.isActive()))
                .findFirst()
                .orElse(null);

        if (community == null) {
            return null;
        }

        return new AddressLocation.Community(
                community.id(),
                community.name()
        );
    }

    private CountryName mapCountryToEnum(String value) {
        if (isBlank(value)) {
            return null;
        }

        return switch (value.trim().toUpperCase()) {
            case "SAU", "113", "SAUDI", "SAUDI ARABIA", "SAUDI_ARABIA" -> CountryName.SAUDI_ARABIA;
            case "JOR", "JORDAN" -> CountryName.JORDAN;
            case "PSE", "PALESTINE" -> CountryName.PALESTINE;
            case "EGY", "EGYPT" -> CountryName.EGYPT;
            case "ARE", "UAE", "UNITED ARAB EMIRATES", "UNITED_ARAB_EMIRATES" -> CountryName.UNITED_ARAB_EMIRATES;
            case "KWT", "KUWAIT" -> CountryName.KUWAIT;
            case "QAT", "QATAR" -> CountryName.QATAR;
            case "BHR", "BAHRAIN" -> CountryName.BAHRAIN;
            case "OMN", "OMAN" -> CountryName.OMAN;
            case "YEM", "YEMEN" -> CountryName.YEMEN;
            case "IND", "INDIA" -> CountryName.INDIA;
            case "PAK", "PAKISTAN" -> CountryName.PAKISTAN;
            case "BGD", "BANGLADESH" -> CountryName.BANGLADESH;
            case "PHL", "PHILIPPINES" -> CountryName.PHILIPPINES;
            case "LBN", "LEBANON" -> CountryName.LEBANON;
            case "SYR", "SYRIA" -> CountryName.SYRIA;
            case "IRQ", "IRAQ" -> CountryName.IRAQ;
            case "USA", "UNITED STATES", "UNITED STATES OF AMERICA", "UNITED_STATES_OF_AMERICA" -> CountryName.UNITED_STATES_OF_AMERICA;
            case "GBR", "UNITED KINGDOM", "UNITED_KINGDOM" -> CountryName.UNITED_KINGDOM;
            default -> tryEnum(value);
        };
    }

    private CountryName tryEnum(String value) {
        try {
            return CountryName.valueOf(
                    value.trim()
                            .toUpperCase()
                            .replace(" ", "_")
                            .replace("-", "_")
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveStreetName(CchiBeneficiaryData b) {
        if (!isBlank(b.streetLine())) {
            return clean(b.streetLine());
        }

        if (!isBlank(b.addressLine())) {
            return clean(b.addressLine());
        }

        if (!isBlank(b.city())) {
            return clean(b.city());
        }

        if (!isBlank(b.state())) {
            return clean(b.state());
        }

        if (!isBlank(b.country())) {
            return clean(b.country());
        }

        return "N/A";
    }

    private boolean hasAnyAddressData(CchiBeneficiaryData b) {
        return !isBlank(b.addressLine())
                || !isBlank(b.streetLine())
                || !isBlank(b.city())
                || !isBlank(b.state())
                || !isBlank(b.country())
                || !isBlank(b.postalCode());
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}