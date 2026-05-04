package com.dazzle.asklepios.service.helper;


import com.dazzle.asklepios.client.setup.AllergenClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class AllergenHelper{

    private final AllergenClient allergenClient;

    public AllergenHelper(AllergenClient allergenClient) {
        this.allergenClient = allergenClient;
    }

    public void validateAllergenExists(Long allergenId) {
        try {
            allergenClient.existsAllergen(allergenId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Allergen not found: " + allergenId,
                    "Allergen",
                    "notfound"
            );
        }
    }
}
