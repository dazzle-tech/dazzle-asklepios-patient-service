package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.VaccineBrandClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class VaccineBrandHelper {

    private final VaccineBrandClient vaccineBrandClient;
    public VaccineBrandHelper(VaccineBrandClient vaccineBrandClient) {
        this.vaccineBrandClient = vaccineBrandClient;
    }

    public void validateVaccineBrandExists(Long id) {
        try {
            vaccineBrandClient.existsVaccineBrand(id);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "vaccineBrand not found: " + id,
                    "vaccineBrand",
                    "notfound"
            );
        }
    }
}
