package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.ICDTreeClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class ICDTreeHelper {

    private final ICDTreeClient icdTreeClient;

    public ICDTreeHelper(ICDTreeClient icdTreeClient) {
        this.icdTreeClient = icdTreeClient;
    }

    public void validateICDDiagnosisExists(Long icdDiadnosisId) {
        try {
            icdTreeClient.existsICDDiagnosis(icdDiadnosisId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "ICD Diagnosis not found: " + icdTreeClient,
                    "ICDDiagnosis",
                    "notfound"
            );
        }
    }

}
