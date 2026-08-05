package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.BillingPatientViewService;
import com.dazzle.asklepios.service.dto.billing.EncounterBillingSummary;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/billing")
public class BillingPatientViewController {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    BillingPatientViewController.class
            );

    private final BillingPatientViewService
            billingPatientViewService;

    @GetMapping(
            "/encounters/{encounterId}/summary"
    )
    public ResponseEntity<EncounterBillingSummary>
    getEncounterSummary(
            @PathVariable("encounterId")
            @NotNull
            Long encounterId
    ) {
        LOG.debug(
                "REST request to get encounter billing summary encounterId={}",
                encounterId
        );

        EncounterBillingSummary summary =
                billingPatientViewService
                        .getEncounterSummary(
                                encounterId
                        );

        return ResponseEntity.ok(summary);
    }
}
