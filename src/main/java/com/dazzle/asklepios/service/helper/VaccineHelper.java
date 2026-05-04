package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.VaccineClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class VaccineHelper {

    private final VaccineClient vaccineClient;
    public VaccineHelper(VaccineClient vaccineClient) {
        this.vaccineClient = vaccineClient;
    }

    public void validateVaccineExists(Long id) {
        try {
            vaccineClient.existsVaccine(id);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Vaccine not found: " + id,
                    "Vaccine",
                    "notfound"
            );
        }
    }
}
