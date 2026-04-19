package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.DiagnosticTestClient;
import com.dazzle.asklepios.client.setup.dto.DiagnosticTestSetupDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;


@Service
public class DiagnosticTestHelper {

    private final DiagnosticTestClient diagnosticTestClient;

    public DiagnosticTestHelper(DiagnosticTestClient diagnosticTestClient) {
        this.diagnosticTestClient = diagnosticTestClient;
    }

    public DiagnosticTestSetupDTO getDiagnosticTest(Long testId) {
        try {
            return diagnosticTestClient.getDiagnosticTest(testId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "DiagnosticTest not found: " + testId,
                    "DiagnosticTest",
                    "notfound"
            );
        }
    }
}
