package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.ProcedureClient;
import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class ProcedureHelper {
    private final ProcedureClient procedureClient;

    public ProcedureHelper(ProcedureClient procedureClient) {
        this.procedureClient = procedureClient;
    }

    public void validateProcedureExists(Long procedureId) {
        try {
            procedureClient.getProcedure(procedureId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Procedure not found: " + procedureId,
                    "Procedure",
                    "notfound"
            );
        }
    }
}
