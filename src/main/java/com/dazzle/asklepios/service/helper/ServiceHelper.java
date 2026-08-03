package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ServiceHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceHelper.class);

    private final ServiceClient serviceClient;

    public ServiceHelper(ServiceClient serviceClient) {
        this.serviceClient = serviceClient;
    }

    public void validateServiceExists(Long serviceId) {
        LOG.debug("Validating service exists. serviceId={}", serviceId);

        try {
            ServiceSetupDTO service = serviceClient.getServiceDetails(serviceId);

            if (service == null || service.id() == null) {
                LOG.warn("Service validation failed. Empty response. serviceId={}", serviceId);

                throw new NotFoundAlertException(
                        "Service not found: " + serviceId,
                        "service",
                        "notfound"
                );
            }

            LOG.debug("Service validation success. serviceId={}, name={}, price={}, currency={}",
                    service.id(), service.name(), service.price(), service.currency());

        } catch (FeignException.NotFound ex) {
            LOG.warn("Service validation failed. Not found. serviceId={}", serviceId);

            throw new NotFoundAlertException(
                    "Service not found: " + serviceId,
                    "service",
                    "notfound"
            );
        }
    }

    public ServiceSetupDTO getService(Long serviceId) {
        try {
            return serviceClient.getServiceDetails(serviceId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Service not found: " + serviceId,
                    "service",
                    "notfound"
            );
        }
    }
}