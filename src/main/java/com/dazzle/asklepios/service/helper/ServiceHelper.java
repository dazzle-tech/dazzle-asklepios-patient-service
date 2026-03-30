package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class ServiceHelper {

    private final ServiceClient serviceClient;
    public ServiceHelper(ServiceClient serviceClient) {
        this.serviceClient = serviceClient;
    }

    public void validateServiceExists(Long serviceId) {
        try {
            serviceClient.getService(serviceId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Service not found: " + serviceId,
                    "service",
                    "notfound"
            );
        }
    }
}
