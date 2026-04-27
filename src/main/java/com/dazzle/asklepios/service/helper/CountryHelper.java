package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.BedClient;
import com.dazzle.asklepios.client.setup.CountryClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class CountryHelper {

    private final CountryClient countryClient;

    public CountryHelper(CountryClient countryClient) {
        this.countryClient = countryClient;
    }

    public void validateCountryExists(Long countryId) {
        try {
            countryClient.existsCountry(countryId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Country not found: " + countryId,
                    "Country",
                    "notfound"
            );
        }
    }

}
