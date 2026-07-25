package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.DefaultServicePreparationService;
import com.dazzle.asklepios.service.EncounterBillingSummaryService;
import com.dazzle.asklepios.service.dto.billing.EncounterBillingSummary;
import com.dazzle.asklepios.service.dto.billing.PrepareDefaultServicesRequest;
import com.dazzle.asklepios.service.dto.billing.PrepareDefaultServicesResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/patient/billing/encounters")
public class DefaultServiceBillingController {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    DefaultServiceBillingController.class
            );

    private final DefaultServicePreparationService
            defaultServicePreparationService;

    private final EncounterBillingSummaryService
            encounterBillingSummaryService;

    @GetMapping("/{encounterId}/summary")
    public ResponseEntity<EncounterBillingSummary>
    getEncounterBillingSummary(
            @PathVariable("encounterId")
            @NotNull
            Long encounterId
    ) {
        LOG.debug(
                "REST request to get encounter billing summary encounterId={}",
                encounterId
        );

        return ResponseEntity.ok(
                encounterBillingSummaryService
                        .getByEncounterId(
                                encounterId
                        )
        );
    }

    @PostMapping("/{encounterId}/prepare-default-services")
    public ResponseEntity<PrepareDefaultServicesResult>
    prepareDefaultServices(
            @PathVariable("encounterId")
            @NotNull
            Long encounterId,

            @Valid
            @RequestBody
            @NotNull
            PrepareDefaultServicesRequest request
    ) {
        LOG.debug(
                "REST request to prepare encounter default services "
                        + "encounterId={} patientId={} coverageType={} "
                        + "itemCount={} requestId={}",
                encounterId,
                request.patientId(),
                request.coverageType(),
                request.items() == null
                        ? 0
                        : request.items().size(),
                request.requestId()
        );

        return ResponseEntity.ok(
                defaultServicePreparationService
                        .prepare(
                                encounterId,
                                request
                        )
        );
    }
}
