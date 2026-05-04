package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.BedClient;
import com.dazzle.asklepios.client.setup.CDTCodeClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class CDTCodeHelper {

    private final CDTCodeClient cdtCodeClient;

    public CDTCodeHelper(CDTCodeClient cdtCodeClient) {
        this.cdtCodeClient = cdtCodeClient;
    }

    public void validateCDTCodeExists(Long cdtCodeId) {
        try {
            cdtCodeClient.existsCDTCode(cdtCodeId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "CDT Code not found: " + cdtCodeId,
                    "CDTCode",
                    "notfound"
            );
        }
    }

}
