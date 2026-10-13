package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.DentalActionClient;
import com.dazzle.asklepios.client.setup.dto.DentalActionSetupDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class DentalActionHelper {

    private final DentalActionClient dentalActionClient;

    public DentalActionHelper(DentalActionClient dentalActionClient) {
        this.dentalActionClient = dentalActionClient;
    }

    public DentalActionSetupDTO getDentalAction(Long dentalActionId) {
        try {
            return dentalActionClient.getDentalAction(dentalActionId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Dental action not found: " + dentalActionId,
                    "dentalAction",
                    "notfound"
            );
        }
    }
}
