package com.dazzle.asklepios.service.helper;


import com.dazzle.asklepios.client.setup.MedicationCategoryClassClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class MedicationCategoryClassHelper {

    private final MedicationCategoryClassClient medicationCategoryClassClient;

    public MedicationCategoryClassHelper(MedicationCategoryClassClient medicationCategoryClassClient) {
        this.medicationCategoryClassClient = medicationCategoryClassClient;
    }

    public void validateMedicationCategoryClassExists(Long medicationCategoryClassId) {
        try {
            medicationCategoryClassClient.existsMedicationCategoryClass(medicationCategoryClassId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "medication Category Class not found: " + medicationCategoryClassId,
                    "medicationCategoryClass",
                    "notfound"
            );
        }
    }
}
