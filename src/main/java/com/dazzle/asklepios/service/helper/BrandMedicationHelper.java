package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.ActiveIngredientClient;
import com.dazzle.asklepios.client.setup.BrandMedicationClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class BrandMedicationHelper {

    private final BrandMedicationClient brandMedicationClient;

    public BrandMedicationHelper(BrandMedicationClient brandMedicationClient) {
        this.brandMedicationClient = brandMedicationClient;
    }

    public void validateBrandMedicationExists(Long brandMedicationId) {
        try {
            brandMedicationClient.existsBrandMedication(brandMedicationId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Brand Medication not found: " + brandMedicationId,
                    "BrandMedication",
                    "notfound"
            );
        }
    }

}
