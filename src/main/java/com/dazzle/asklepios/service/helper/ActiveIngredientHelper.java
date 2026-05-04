package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.ActiveIngredientClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class ActiveIngredientHelper {

    private final ActiveIngredientClient activeIngredientClient;

    public ActiveIngredientHelper(ActiveIngredientClient activeIngredientClient) {
        this.activeIngredientClient = activeIngredientClient;
    }

    public void validateActiveIngredientExists(Long activeIngredientId) {
        try {
            activeIngredientClient.existsActiveIngredient(activeIngredientId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Active Ingredient not found: " + activeIngredientId,
                    "ActiveIngredient",
                    "notfound"
            );
        }
    }

}
