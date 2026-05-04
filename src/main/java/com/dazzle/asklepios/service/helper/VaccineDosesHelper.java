package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.VaccineDosesClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class VaccineDosesHelper{

    private final VaccineDosesClient vaccineDosesClient;
    public VaccineDosesHelper(VaccineDosesClient vaccineDosesClient) {
        this.vaccineDosesClient = vaccineDosesClient;
    }

    public void validateVaccineDosesExists(Long id) {
        try {
            vaccineDosesClient.existsVaccineDoses(id);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "VaccineDoses not found: " + id,
                    "VaccineDoses",
                    "notfound"
            );
        }
    }
}
