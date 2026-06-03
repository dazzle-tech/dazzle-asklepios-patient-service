package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.PractitionerClient;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class PractitionerHelper {

    private final PractitionerClient practitionerClient;
    public PractitionerHelper(final PractitionerClient practitionerClient) {
      this.practitionerClient = practitionerClient;
    }

    public void validatePractitionerExists(Long practitionerId) {
        try {
            practitionerClient.existPractitioner(practitionerId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Practitioner not found: " + practitionerId,
                    "practitioner",
                    "notfound"
            );
        }
    }

    public PractitionerDTO getPractitioner(Long practitionerId) {
        try {
            return practitionerClient.getPractitioner(practitionerId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Practitioner not found: " + practitionerId,
                    "practitioner",
                    "notfound"
            );
        }
    }
}
