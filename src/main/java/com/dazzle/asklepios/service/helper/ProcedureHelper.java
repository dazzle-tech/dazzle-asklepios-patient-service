package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.ProcedureClient;
import com.dazzle.asklepios.client.setup.dto.ProcedureSetupDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class ProcedureHelper {
    private final ProcedureClient procedureClient;

    public ProcedureHelper(ProcedureClient procedureClient) {
        this.procedureClient = procedureClient;
    }

    public void validateProcedureExists(Long procedureId) {
        getProcedure(procedureId);
    }

    public ProcedureSetupDTO getProcedure(Long id) {
        try {
            return procedureClient.getProcedure(id);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Procedure not found: " + id,
                    "Procedure",
                    "notfound"
            );
        }
    }
}
