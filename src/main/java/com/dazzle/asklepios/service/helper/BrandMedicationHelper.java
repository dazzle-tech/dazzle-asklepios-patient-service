package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.ActiveIngredientClient;
import com.dazzle.asklepios.client.setup.BrandMedicationClient;
import com.dazzle.asklepios.client.setup.dto.BrandMedicationSetupDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

import java.util.List;

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

    /**
     * The brand, which must be an active brand of the given active ingredient.
     */
    public BrandMedicationSetupDTO getActiveBrandOfActiveIngredient(
            Long brandMedicationId,
            Long activeIngredientId
    ) {
        List<BrandMedicationSetupDTO> brands =
                brandMedicationClient.getByActiveIds(List.of(activeIngredientId));

        BrandMedicationSetupDTO brand = (brands == null ? List.<BrandMedicationSetupDTO>of() : brands)
                .stream()
                .filter(candidate -> brandMedicationId.equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new BadRequestAlertException(
                        "Brand medication " + brandMedicationId + " is not a brand of the order's active ingredient",
                        "BrandMedication",
                        "activeIngredient.mismatch"
                ));

        if (Boolean.FALSE.equals(brand.isActive())) {
            throw new BadRequestAlertException(
                    "Brand medication " + brandMedicationId + " is inactive",
                    "BrandMedication",
                    "inactive"
            );
        }

        return brand;
    }

}
