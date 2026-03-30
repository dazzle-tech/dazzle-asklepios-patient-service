package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.FacilityClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class FacilityHelper {

    private final FacilityClient facilityClient;
    public FacilityHelper(final FacilityClient facilityClient) {
        this.facilityClient = facilityClient;
    }

    public void validateFacilityExists(Long facilityId) {
        try {
            facilityClient.getFacility(facilityId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Facility not found: " + facilityId,
                    "facility",
                    "notfound"
            );
        }
    }
}
