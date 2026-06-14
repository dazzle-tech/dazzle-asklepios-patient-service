package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.CountryClient;
import com.dazzle.asklepios.client.setup.dto.CountryDTO;
import com.dazzle.asklepios.domain.enumeration.CountryName;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class CountryHelper {

    private final CountryClient countryClient;

    public CountryHelper(CountryClient countryClient) {
        this.countryClient = countryClient;
    }

    public CountryDTO getCountryOrThrow(Long countryId) {
        try {
            CountryDTO country = countryClient.getCountry(countryId).getBody();

            if (country == null) {
                throw notFound(countryId);
            }

            return country;

        } catch (feign.FeignException.NotFound ex) {
            throw notFound(countryId);
        }
    }

    private NotFoundAlertException notFound(Long countryId) {
        return new NotFoundAlertException(
                "Country not found: " + countryId,
                "Country",
                "notfound"
        );
    }

    public CountryName getCountryName(Long countryId) {
        return getCountryOrThrow(countryId).name();
    }

}
