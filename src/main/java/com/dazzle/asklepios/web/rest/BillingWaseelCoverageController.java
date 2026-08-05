package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.integration.waseel.dto.WaseelCoverageDetails;
import com.dazzle.asklepios.integration.waseel.service.WaseelCoverageQueryService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/patient/billing")
public class BillingWaseelCoverageController {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingWaseelCoverageController.class
            );

    private final WaseelCoverageQueryService
            waseelCoverageQueryService;

    @GetMapping("/patients/{patientId}/waseel-coverage")
    public ResponseEntity<WaseelCoverageDetails>
    getWaseelCoverage(
            @PathVariable("patientId")
            @NotNull
            Long patientId,

            @RequestParam(
                    required = false
            )
            Long patientInsuranceId
    ) {
        LOG.debug(
                "REST request Waseel coverage patientId={} patientInsuranceId={}",
                patientId,
                patientInsuranceId
        );

        return ResponseEntity.ok(
                waseelCoverageQueryService
                        .getLatestForPatient(
                                patientId,
                                patientInsuranceId
                        )
        );
    }
}
